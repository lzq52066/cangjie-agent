package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 链路聚合 Mapper：把 trace_record / llm_trace / agent_run 三张表按 traceId 归并成一行链路摘要。
 * <p>
 * 跨表聚合无法用 MyBatis-Plus Wrapper 表达，SQL 统一放在 resources/mapper/TraceLinkMapper.xml。
 * 手工分页（LIMIT/OFFSET + 独立 count），避免分页插件改写 CTE 与 UNION 后的语句。
 */
@Mapper
public interface TraceLinkMapper {

    /**
     * 链路摘要分页，按开始时间倒序
     */
    List<TraceLinkVO> selectTraceLinks(@Param("q") TraceLinkQueryDTO query,
                                       @Param("limit") long limit,
                                       @Param("offset") long offset);

    /**
     * 同筛选条件下的链路总数（按 traceId 去重）
     */
    long countTraceLinks(@Param("q") TraceLinkQueryDTO query);

    /**
     * 单条链路摘要。入参只需 linkId，其余条件留空表示不叠加时间与归属筛选。
     */
    TraceLinkVO selectTraceLink(@Param("q") TraceLinkQueryDTO query);
}
