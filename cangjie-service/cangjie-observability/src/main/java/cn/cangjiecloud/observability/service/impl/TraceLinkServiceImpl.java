package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.TraceLinkMapper;
import cn.cangjiecloud.observability.service.IAgentRunService;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import cn.cangjiecloud.observability.service.ITraceLinkService;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 链路聚合查询实现。
 * <p>
 * 列表与摘要走 {@link TraceLinkMapper} 的跨表 UNION 聚合；明细按链路键把三类节点各自取回，
 * 层级交给前端用 parentRunId / spanId 还原。孤立链路（不带 traceId 的后台调用等）用
 * {@code #kind#记录ID} 键表示，展开时按主键回查单条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceLinkServiceImpl implements ITraceLinkService {

    /**
     * 未指定起始时间时的回溯窗口：三张表都是按 start_time 倒序索引的留痕表，
     * 不设下限会让聚合扫全表，页面首屏等不起。
     */
    private static final int DEFAULT_WINDOW_DAYS = 7;

    private static final String ORPHAN_PREFIX = "#";

    private final TraceLinkMapper traceLinkMapper;
    private final ITraceRecordService traceRecordService;
    private final IAgentRunService agentRunService;
    private final ILlmTraceService llmTraceService;

    @Override
    public IPage<TraceLinkVO> pageQuery(TraceLinkQueryDTO query) {
        // 单条链路专用的裁剪条件不参与列表查询
        query.setLinkId(null);
        query.setOrphanId(null);
        if (query.getStartTime() == null) {
            query.setEndTime(query.getEndTime() == null ? LocalDateTime.now() : query.getEndTime());
            query.setStartTime(query.getEndTime().minusDays(DEFAULT_WINDOW_DAYS));
        }
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        long total = traceLinkMapper.countTraceLinks(query);
        List<TraceLinkVO> records = total == 0
                ? List.of()
                : traceLinkMapper.selectTraceLinks(query, pageSize, (pageNum - 1) * pageSize);
        // 总数单独查过，这里关掉分页插件的自动 count，避免把聚合语句再跑一遍
        Page<TraceLinkVO> page = new Page<>(pageNum, pageSize, false);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    @Override
    public TraceLinkDetailVO detail(String linkId) {
        if (!StringUtils.hasText(linkId)) {
            return null;
        }
        boolean orphan = linkId.startsWith(ORPHAN_PREFIX);
        // 孤立链路的 parts = ["", kind, 记录ID]
        String[] parts = orphan ? linkId.split("#", 3) : new String[0];
        if (orphan && (parts.length < 3 || !StringUtils.hasText(parts[2]))) {
            return null;
        }
        TraceLinkQueryDTO probe = new TraceLinkQueryDTO();
        // linkId 恒为聚合键（孤立链路即 #kind#记录ID）；orphanId 只额外用于按主键裁剪各分支扫描量
        probe.setLinkId(linkId);
        if (orphan) {
            probe.setOrphanId(parts[2]);
        }

        TraceLinkVO summary = traceLinkMapper.selectTraceLink(probe);
        if (summary == null) {
            return null;
        }
        TraceLinkDetailVO detail = new TraceLinkDetailVO();
        detail.setSummary(summary);
        detail.setSpans(new ArrayList<>());
        detail.setRuns(new ArrayList<>());
        detail.setBackgroundLlmTraces(new ArrayList<>());

        if (orphan) {
            // 孤立链路本身就是一条记录，取回它自己即可
            String kind = parts[1];
            String id = parts[2];
            switch (kind) {
                case "span" -> {
                    TraceRecordEntity span = traceRecordService.getById(id);
                    if (span != null) {
                        detail.getSpans().add(span);
                    }
                }
                case "run" -> addRunDetail(detail, id);
                case "llm" -> {
                    LlmTraceEntity trace = llmTraceService.getBrief(id);
                    if (trace != null) {
                        detail.getBackgroundLlmTraces().add(trace);
                    }
                }
                default -> log.debug("未知的孤立链路键: {}", linkId);
            }
            return detail;
        }

        detail.setSpans(traceRecordService.list(new LambdaQueryWrapper<TraceRecordEntity>()
                .eq(TraceRecordEntity::getTraceId, linkId)
                .orderByAsc(TraceRecordEntity::getStartTime)));
        List<AgentRunEntity> runs = agentRunService.list(new LambdaQueryWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getTraceId, linkId)
                .orderByAsc(AgentRunEntity::getStartTime));
        for (AgentRunEntity run : runs) {
            addRunDetail(detail, run.getId());
        }
        detail.setBackgroundLlmTraces(llmTraceService.listBackgroundByTrace(linkId));
        return detail;
    }

    /**
     * 追加一次 run 的完整明细。
     * <p>
     * 子 run 与父 run 同属一条链路、已在 runs 列表中平铺，这里清掉 children，
     * 免得前端把同一个 run 渲染两遍。
     */
    private void addRunDetail(TraceLinkDetailVO detail, String runId) {
        AgentRunDetailVO runDetail = agentRunService.detail(runId);
        if (runDetail == null) {
            return;
        }
        runDetail.setChildren(null);
        detail.getRuns().add(runDetail);
    }
}
