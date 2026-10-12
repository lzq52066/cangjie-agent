package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TraceRecordMapper extends BaseMapper<TraceRecordEntity> {

    /**
     * 按保留期物理删除 span。
     * <p>
     * 必须绕过逻辑删除：{@code @TableLogic} 只把 deleted 置 1，载荷三列（text）一个字节都回收不了，
     * 留着它们等于让 trace_record 随对话量单调增长，把「载荷不进列表」换来的收益全部吃回去。
     * 与 system_metric / agent_run 一样用子查询 LIMIT 分批删。
     */
    @Delete("DELETE FROM trace_record WHERE id IN ("
            + "SELECT id FROM trace_record WHERE start_time < #{cutoff} LIMIT #{batchSize})")
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
