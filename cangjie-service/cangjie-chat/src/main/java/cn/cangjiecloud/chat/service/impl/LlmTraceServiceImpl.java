package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.LlmTraceEntity;
import cn.cangjiecloud.chat.mapper.LlmTraceMapper;
import cn.cangjiecloud.chat.service.ILlmTraceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LlmTraceServiceImpl extends ServiceImpl<LlmTraceMapper, LlmTraceEntity> implements ILlmTraceService {
}