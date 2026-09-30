package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.ModelSettings;
import cn.cangjiecloud.core.harness.ResumeState;
import cn.cangjiecloud.core.harness.context.ContextResult;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatHarnessContextFactory} 单元测试：覆盖新执行输入装配与检查点恢复装配，
 * 包括模型名解析回落、应用加载失败容错等分支。
 */
@ExtendWith(MockitoExtension.class)
class ChatHarnessContextFactoryTest {

    /** 应用服务桩 */
    @Mock
    private IApplicationService applicationService;

    /** 模型服务桩 */
    @Mock
    private IModelService modelService;

    /** 工具规格装配器桩 */
    @Mock
    private ToolSpecAssembler toolSpecAssembler;

    /** 配置解析器桩 */
    @Mock
    private HarnessConfigResolver configResolver;

    /** 被测试装配工厂 */
    private ChatHarnessContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ChatHarnessContextFactory(
                applicationService, modelService, toolSpecAssembler, configResolver);
    }

    /**
     * 构造应用实体
     */
    private ApplicationEntity application(String modelId) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId("app-1");
        entity.setName("助手");
        entity.setModelId(modelId);
        return entity;
    }

    @Nested
    class NewRequest {

        /** 完整装配新执行输入：身份、上下文、工具集、采样与循环策略均被透传 */
        @Test
        void shouldAssembleNewRequest() {
            ApplicationEntity application = application("model-1");
            List<ChatMessage> messages = List.of(ChatMessage.user("hi"));
            ContextResult context = ContextResult.of(messages);
            HarnessConfig config = HarnessConfig.builder().build();
            List<ToolSpecification> tools = List.of(ToolSpecification.builder().name("t").build());
            when(configResolver.parse(application)).thenReturn(config);
            when(toolSpecAssembler.assemble(application)).thenReturn(tools);
            when(toolSpecAssembler.knowledgeBaseIds(application)).thenReturn(List.of("kb1"));
            when(configResolver.modelSettings(application))
                    .thenReturn(ModelSettings.builder().temperature(0.5).build());
            when(configResolver.loopPolicy(config))
                    .thenReturn(LoopPolicy.builder().maxRounds(3).build());
            ModelEntity model = new ModelEntity();
            model.setName("模型A");
            when(modelService.getById("model-1")).thenReturn(model);
            AtomicBoolean cancel = new AtomicBoolean();

            HarnessRequest request = factory.newRequest(
                    application, context, true, cancel, "session-1", "user-1", "trace-1");

            assertThat(request.getApplicationId()).isEqualTo("app-1");
            assertThat(request.getApplicationName()).isEqualTo("助手");
            assertThat(request.getSessionId()).isEqualTo("session-1");
            assertThat(request.getUserId()).isEqualTo("user-1");
            assertThat(request.getTraceId()).isEqualTo("trace-1");
            assertThat(request.getModelId()).isEqualTo("model-1");
            assertThat(request.getModelName()).isEqualTo("模型A");
            assertThat(request.getHarnessType()).isEqualTo("chat");
            assertThat(request.isStream()).isTrue();
            assertThat(request.getTools()).isEqualTo(tools);
            assertThat(request.getKnowledgeBaseIds()).containsExactly("kb1");
            assertThat(request.getContextMessages()).isEqualTo(messages);
            assertThat(request.getCancelFlag()).isSameAs(cancel);
            assertThat(request.getConfig()).isSameAs(config);
            assertThat(request.getModelSettings().getTemperature()).isEqualTo(0.5);
            assertThat(request.getLoopPolicy().getMaxRounds()).isEqualTo(3);
        }

        /** 模型 ID 缺失时模型名回落为“默认模型”，取消信号为 null 时新建 */
        @Test
        void shouldFallbackModelNameAndCancelFlag() {
            ApplicationEntity application = application(null);
            when(toolSpecAssembler.assemble(application)).thenReturn(List.of());
            when(toolSpecAssembler.knowledgeBaseIds(application)).thenReturn(List.of());

            HarnessRequest request = factory.newRequest(
                    application, ContextResult.of(null), false, null, null, null, null);

            assertThat(request.getModelName()).isEqualTo("默认模型");
            assertThat(request.getCancelFlag()).isNotNull();
            verify(modelService, never()).getById(anyString());
        }

        /** 查询模型失败或模型无名称时回落使用模型 ID */
        @Test
        void shouldFallbackToModelIdWhenLookupFails() {
            ApplicationEntity application = application("model-x");
            when(modelService.getById("model-x")).thenThrow(new RuntimeException("boom"));
            when(toolSpecAssembler.assemble(any())).thenReturn(List.of());
            when(toolSpecAssembler.knowledgeBaseIds(any())).thenReturn(List.of());

            HarnessRequest request = factory.newRequest(
                    application, ContextResult.of(null), false, null, null, null, null);

            assertThat(request.getModelName()).isEqualTo("model-x");
        }
    }

    @Nested
    class Resolve {

        /**
         * 构造挂起 run 快照
         */
        private AgentRunRecorder.PausedRun pausedRun(String appId, String appName, String harnessType) {
            return AgentRunRecorder.PausedRun.builder()
                    .runId("run-1").traceId("trace-1")
                    .applicationId(appId).applicationName(appName)
                    .sessionId("session-1").userId("user-1")
                    .modelId("model-1").modelName("模型A")
                    .harnessType(harnessType)
                    .state(ResumeState.builder().build())
                    .build();
        }

        /** 恢复装配：沿用检查点身份字段，stream 固定为 false，并带 resume 状态 */
        @Test
        void shouldAssembleResumedRequest() {
            ApplicationEntity application = application("model-1");
            AgentRunRecorder.PausedRun pausedRun = pausedRun("app-1", "旧名称", "chat");
            HarnessConfig config = HarnessConfig.builder().build();
            when(applicationService.getById("app-1")).thenReturn(application);
            when(configResolver.parse(application)).thenReturn(config);
            when(toolSpecAssembler.assemble(application)).thenReturn(List.of());
            when(toolSpecAssembler.knowledgeBaseIds(application)).thenReturn(List.of());
            when(configResolver.modelSettings(application)).thenReturn(ModelSettings.builder().build());
            when(configResolver.loopPolicy(config)).thenReturn(LoopPolicy.builder().build());

            HarnessRequest request = factory.resolve(pausedRun, true, "ok");

            assertThat(request.getRunId()).isEqualTo("run-1");
            assertThat(request.getApplicationName()).isEqualTo("旧名称");
            assertThat(request.isStream()).isFalse();
            assertThat(request.getResume()).isNotNull();
            assertThat(request.getConfig()).isSameAs(config);
        }

        /** 挂起快照缺应用名/harnessType 时，应用名取应用实体、类型回落 chat */
        @Test
        void shouldFillMissingNameAndHarnessType() {
            ApplicationEntity application = application("model-1");
            AgentRunRecorder.PausedRun pausedRun = pausedRun("app-1", null, null);
            when(applicationService.getById("app-1")).thenReturn(application);
            when(toolSpecAssembler.assemble(application)).thenReturn(List.of());
            when(toolSpecAssembler.knowledgeBaseIds(application)).thenReturn(List.of());
            when(configResolver.modelSettings(application)).thenReturn(ModelSettings.builder().build());
            when(configResolver.loopPolicy(any())).thenReturn(LoopPolicy.builder().build());

            HarnessRequest request = factory.resolve(pausedRun, false, "no");

            assertThat(request.getApplicationName()).isEqualTo("助手");
            assertThat(request.getHarnessType()).isEqualTo("chat");
        }

        /** 应用 ID 缺失时不加载应用，按无工具继续 */
        @Test
        void shouldSkipLoadingApplicationWhenIdBlank() {
            AgentRunRecorder.PausedRun pausedRun = pausedRun(null, "name", "chat");

            HarnessRequest request = factory.resolve(pausedRun, true, null);

            verify(applicationService, never()).getById(anyString());
            assertThat(request.getTools()).isEmpty();
        }

        /** 加载应用抛异常时容错返回 null，按无工具继续 */
        @Test
        void shouldTolerateApplicationLoadFailure() {
            AgentRunRecorder.PausedRun pausedRun = pausedRun("app-1", null, "chat");
            when(applicationService.getById("app-1")).thenThrow(new RuntimeException("db down"));

            HarnessRequest request = factory.resolve(pausedRun, true, null);

            assertThat(request.getTools()).isEmpty();
            assertThat(request.getApplicationName()).isNull();
            verify(toolSpecAssembler).assemble(null);
        }
    }
}
