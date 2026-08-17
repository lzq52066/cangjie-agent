package cn.cangjiecloud.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
