package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.dto.TraceLinkDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 链路聚合查询服务：把「调用追踪 / LLM 调用 / Agent 执行」三张平铺表按 traceId 合成一个视图。
 */
public interface ITraceLinkService {

    /**
     * 链路摘要分页（按开始时间倒序）
     */
    IPage<TraceLinkVO> pageQuery(TraceLinkQueryDTO query);

    /**
     * 单条链路明细。
     *
     * @param linkId 链路 ID；无 traceId 的孤立留痕用 {@code #kind#记录ID} 键
     */
    TraceLinkDetailVO detail(String linkId);
}
