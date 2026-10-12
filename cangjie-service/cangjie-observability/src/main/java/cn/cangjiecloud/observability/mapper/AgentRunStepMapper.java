package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AgentRunStepMapper extends BaseMapper<AgentRunStepEntity> {

    /**
     * 按保留期物理删除步骤明细（绕过逻辑删除，子查询 LIMIT 分批）
     */
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
