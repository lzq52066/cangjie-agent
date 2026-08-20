package cn.cangjiecloud.observability.mapper;

import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceRecordMapper extends BaseMapper<TraceRecordEntity> {
}