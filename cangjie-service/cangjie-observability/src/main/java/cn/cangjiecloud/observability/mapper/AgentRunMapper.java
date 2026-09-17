package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.AgentRunEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {

    /**
     * 按保留期物理删除终态 run（进行中的 run 不清理）
     */
    @Delete("DELETE FROM agent_run WHERE id IN ("
            + "SELECT id FROM agent_run WHERE create_time < #{cutoff} "
            + "AND status IN ('completed','failed','cancelled') LIMIT #{batchSize})")
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
