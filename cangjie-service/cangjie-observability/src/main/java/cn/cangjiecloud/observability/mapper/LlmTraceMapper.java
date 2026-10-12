package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface LlmTraceMapper extends BaseMapper<LlmTraceEntity> {

    /**
     * 按保留期物理删除模型调用追踪。
     * <p>
     * prompt_content / response_content 动辄数万字符，逻辑删除回收不了空间；
     * 不跟着 agent_run / system_metric 一起清，这张表会最先膨胀。
     */
    @Delete("DELETE FROM llm_trace WHERE id IN ("
            + "SELECT id FROM llm_trace WHERE start_time < #{cutoff} LIMIT #{batchSize})")
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
