package cn.cangjiecloud.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长期记忆 Mapper：仅继承 MyBatis-Plus {@link BaseMapper}，
 * 所有条件查询与字段更新均通过 Wrapper 在 Service 层完成，不写 SQL。
 */
@Mapper
public interface LongTermMemoryMapper extends BaseMapper<LongTermMemoryEntity> {
}