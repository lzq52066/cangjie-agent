package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SystemMetricMapper extends BaseMapper<SystemMetricEntity> {

    /**
     * 按保留期物理删除历史指标。
     * 必须绕过逻辑删除（BaseEntity 上的 @TableLogic 只会把 deleted 置 1，无法回收空间），
     * 并用子查询 LIMIT 分批，避免单次事务删除过多行。
     */
    int purgeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}