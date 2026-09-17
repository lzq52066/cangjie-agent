package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AgentRunStepMapper extends BaseMapper<AgentRunStepEntity> {

    /**
     * 按保留期物理删除步骤明细（绕过逻辑删除，子查询 LIMIT 分批）
     */
    @Delete("DELETE FROM agent_run_step WHERE id IN ("
            + "SELECT id FROM agent_run_step WHERE create_time < #{cutoff} LIMIT #{batchSize})")
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
