package cn.cangjiecloud.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.chat.service.LongTermMemoryExtractService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IModelService modelService;
    private final HybridRetriever hybridRetriever;
    private final IPromptTemplateService promptTemplateService;
    private final ILongTermMemoryService longTermMemoryService;
    private final LongTermMemoryExtractService longTermMemoryExtractService;
    private final ILlmTraceService llmTraceService;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    private static final int DEFAULT_TOP_K = 5;
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    @Override
    @Transactional(rollbackFor = Exception.class)