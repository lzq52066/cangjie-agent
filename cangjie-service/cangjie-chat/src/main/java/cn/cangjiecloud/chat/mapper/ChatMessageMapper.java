package cn.cangjiecloud.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
