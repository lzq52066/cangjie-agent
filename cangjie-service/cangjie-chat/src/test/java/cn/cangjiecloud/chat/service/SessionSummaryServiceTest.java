package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SessionSummaryService} 单元测试：覆盖开关/会话/阈值/并发抢占等早退分支，
 * 摘要模型解析优先级、默认模型与默认客户端回落、模型失败后的重试恢复，以及摘要落库。
 */
@ExtendWith(MockitoExtension.class)
class SessionSummaryServiceTest {

    /** 模型服务桩 */
    @Mock
    private IModelService modelService;

    /** 会话服务桩 */
    @Mock
    private IChatSessionService chatSessionService;

    /** 消息服务桩 */
    @Mock
    private IChatMessageService chatMessageService;

    /** 模型客户端桩 */
    @Mock
    private OpenAICompatibleClient client;

    /** 被测试摘要服务（每 2 条消息触发一次） */
    private SessionSummaryService service;

    @BeforeEach
    void setUp() {
        service = new SessionSummaryService(modelService, chatSessionService, chatMessageService);
        ReflectionTestUtils.setField(service, "summaryEnabled", true);
        ReflectionTestUtils.setField(service, "summaryEvery", 2);
        ReflectionTestUtils.setField(service, "summaryModelId", "");
    }

    /**
     * 构造会话
     */
    private ChatSessionEntity session(String sessionId, String modelId, Integer messageCount, Integer summaryMsgCount) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setSessionId(sessionId);
        session.setModelId(modelId);
        session.setApplicationId("app-1");
        session.setUserId("u1");
        session.setMessageCount(messageCount);
        session.setSummaryMsgCount(summaryMsgCount);
        return session;
    }

    /**
     * 构造消息
     */
    private ChatMessageEntity message(String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }

    /**
     * 构造模型
     */
    private ModelEntity model(String id, String name) {
        ModelEntity entity = new ModelEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    @Nested
    class Gates {

        /** 摘要开关关闭时直接跳过，不触碰任何依赖 */
        @Test
        void shouldSkipWhenDisabled() {
            ReflectionTestUtils.setField(service, "summaryEnabled", false);

            service.maybeSummarizeAsync("s1", null);

            verifyNoInteractions(chatSessionService, chatMessageService, modelService);
        }

        /** sessionId 空白时直接跳过 */
        @Test
        void shouldSkipBlankSessionId() {
            service.maybeSummarizeAsync(" ", null);

            verifyNoInteractions(chatSessionService);
        }

        /** 同一会话已有摘要任务在途（CAS 抢占失败）时跳过 */
        @Test
        @SuppressWarnings("unchecked")
        void shouldSkipWhenAlreadyInFlight() {
            Set<String> inFlight = (Set<String>) ReflectionTestUtils.getField(service, "inFlight");
            inFlight.add("s1");

            service.maybeSummarizeAsync("s1", null);

            verifyNoInteractions(chatSessionService);
        }

        /** 会话不存在时跳过 */
        @Test
        void shouldSkipWhenSessionMissing() {
            when(chatSessionService.getBySessionId("s1")).thenReturn(null);

            service.maybeSummarizeAsync("s1", null);

            verify(chatMessageService, never()).listBySession(any());
            verify(chatSessionService, never()).updateById(any());
        }

        /** 未摘要消息数不足触发阈值时跳过 */
        @Test
        void shouldSkipWhenBelowThreshold() {
            when(chatSessionService.getBySessionId("s1")).thenReturn(session("s1", "m1", 3, 2));

            service.maybeSummarizeAsync("s1", null);

            verify(chatMessageService, never()).listBySession(any());
            verifyNoInteractions(modelService);
        }

        /** 消息为空时跳过，不调用模型 */
        @Test
        void shouldSkipWhenNoMessages() {
            when(chatSessionService.getBySessionId("s1")).thenReturn(session("s1", "m1", 2, 0));
            when(chatMessageService.listBySession("s1")).thenReturn(List.of());

            service.maybeSummarizeAsync("s1", null);

            verifyNoInteractions(modelService);
            verify(chatSessionService, never()).updateById(any());
        }
    }

    @Nested
    class Summarize {

        /** 达到阈值时生成摘要并回写正文与游标，请求携带既有摘要、role 过滤与 trace 归属 */
        @Test
        void shouldPersistSummaryAndCursor() {
            ChatSessionEntity session = session("s1", "m1", 2, 0);
            session.setSummary("旧摘要内容");
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(
                    message("user", "你好"),
                    message("assistant", "你好呀"),
                    message("system", "系统提示不外发")));
            when(modelService.getById("m1")).thenReturn(model("m1", "测试模型"));
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("  摘要正文  ").build());

            service.maybeSummarizeAsync("s1", null);

            assertThat(session.getSummary()).isEqualTo("摘要正文");
            assertThat(session.getSummaryMsgCount()).isEqualTo(2);
            verify(chatSessionService).updateById(session);

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(client).chat(captor.capture());
            ChatRequest request = captor.getValue();
            assertThat(request.getMessages()).hasSize(2);
            assertThat(request.getMessages().get(1).getContent())
                    .contains("【既有摘要】").contains("旧摘要内容")
                    .contains("【新增对话】").contains("用户: 你好").contains("AI: 你好呀")
                    .doesNotContain("系统提示不外发");
            assertThat(request.getTraceContext().getRequestId()).isEqualTo("session-summary-s1");
            assertThat(request.getTraceContext().getAppId()).isEqualTo("app-1");
            assertThat(request.getTraceContext().getUserId()).isEqualTo("u1");
            assertThat(request.getTraceContext().getModelId()).isEqualTo("m1");
            assertThat(request.getTraceContext().getModelName()).isEqualTo("测试模型");
        }

        /** 配置的摘要模型优先于会话模型 */
        @Test
        void shouldPreferConfiguredSummaryModel() {
            ReflectionTestUtils.setField(service, "summaryModelId", "summary-m");
            ChatSessionEntity session = session("s1", "sess-m", 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getById("summary-m")).thenReturn(model("summary-m", "摘要模型"));
            when(modelService.getClient("summary-m")).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("摘要").build());

            service.maybeSummarizeAsync("s1", null);

            verify(modelService, never()).getById("sess-m");
            assertThat(session.getSummary()).isEqualTo("摘要");
        }

        /** 会话未绑定时回落调用方传入的兜底模型 */
        @Test
        void shouldUseFallbackModelIdWhenSessionModelMissing() {
            ChatSessionEntity session = session("s1", null, 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getById("fb-1")).thenReturn(model("fb-1", "兜底模型"));
            when(modelService.getClient("fb-1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("摘要").build());

            service.maybeSummarizeAsync("s1", "fb-1");

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(client).chat(captor.capture());
            assertThat(captor.getValue().getTraceContext().getModelId()).isEqualTo("fb-1");
        }

        /** 模型 ID 完全缺失时使用默认模型，并回填其 ID 与名称 */
        @Test
        void shouldUseDefaultModelWhenNoModelIdResolved() {
            ChatSessionEntity session = session("s1", null, 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getDefaultModel()).thenReturn(model("d1", "默认模型"));
            when(modelService.getClient("d1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("摘要").build());

            service.maybeSummarizeAsync("s1", null);

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(client).chat(captor.capture());
            assertThat(captor.getValue().getTraceContext().getModelId()).isEqualTo("d1");
            assertThat(captor.getValue().getTraceContext().getModelName()).isEqualTo("默认模型");
        }

        /** 默认模型也不存在时退回默认客户端，trace 的模型字段留空 */
        @Test
        void shouldUseDefaultClientWhenModelEntityMissing() {
            ChatSessionEntity session = session("s1", null, 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getDefaultModel()).thenReturn(null);
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("摘要").build());

            service.maybeSummarizeAsync("s1", null);

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(client).chat(captor.capture());
            assertThat(captor.getValue().getTraceContext().getModelId()).isNull();
            assertThat(session.getSummary()).isEqualTo("摘要");
        }

        /** 模型返回空白内容时不落库 */
        @Test
        void shouldSkipUpdateWhenResponseContentBlank() {
            ChatSessionEntity session = session("s1", "m1", 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getById("m1")).thenReturn(model("m1", "测试模型"));
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("   ").build());

            service.maybeSummarizeAsync("s1", null);

            assertThat(session.getSummary()).isNull();
            verify(chatSessionService, never()).updateById(any());
        }

        /** 模型调用异常被吞掉且释放抢占，重试可继续完成摘要 */
        @Test
        void shouldSwallowFailureAndAllowRetry() {
            ChatSessionEntity session = session("s1", "m1", 2, 0);
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            when(chatMessageService.listBySession("s1")).thenReturn(List.of(message("user", "hi")));
            when(modelService.getById("m1")).thenReturn(model("m1", "测试模型"));
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("模型不可用"));

            service.maybeSummarizeAsync("s1", null);

            assertThat(session.getSummary()).isNull();
            verify(chatSessionService, never()).updateById(any());

            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("恢复后的摘要").build());

            service.maybeSummarizeAsync("s1", null);

            assertThat(session.getSummary()).isEqualTo("恢复后的摘要");
            verify(chatSessionService).updateById(session);
        }
    }
}
