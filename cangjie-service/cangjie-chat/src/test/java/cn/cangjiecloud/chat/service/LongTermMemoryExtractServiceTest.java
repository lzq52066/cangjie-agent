package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;
import cn.cangjiecloud.prompt.memory.MemoryDedupProperties;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LongTermMemoryExtractService} 单元测试：覆盖提取入口的早退分支、
 * 模型解析，以及单条记忆的嵌入降级 / 新增 / 强化 / 合并等语义入库路径。
 */
@ExtendWith(MockitoExtension.class)
class LongTermMemoryExtractServiceTest {

    /** 模型服务桩 */
    @Mock
    private IModelService modelService;

    /** 长期记忆服务桩 */
    @Mock
    private ILongTermMemoryService longTermMemoryService;

    /** 记忆合并判定服务桩 */
    @Mock
    private MemoryMergeService memoryMergeService;

    /** OpenAI 兼容客户端桩 */
    @Mock
    private OpenAICompatibleClient client;

    /** 被测服务（使用真实去重参数） */
    private LongTermMemoryExtractService service;

    @BeforeEach
    void setUp() {
        MemoryDedupProperties properties = new MemoryDedupProperties();
        service = new LongTermMemoryExtractService(
                modelService, longTermMemoryService, memoryMergeService, properties);
    }

    /**
     * 构造一个启用记忆、绑定模型的应用
     */
    private ApplicationEntity application(boolean memoryEnabled) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId("app-1");
        application.setName("助手应用");
        application.setModelId("m-1");
        application.setTemperature(0.5);
        application.setMemoryEnabled(memoryEnabled);
        return application;
    }

    /**
     * 构造消息实体
     */
    private ChatMessageEntity message(String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setContent(content);
        return message;
    }

    /**
     * 让应用绑定模型可用，并让模型客户端返回指定正文
     */
    private void modelFlowReturns(String content) {
        ModelEntity model = new ModelEntity();
        model.setId("m-1");
        model.setName("模型一号");
        when(modelService.getById("m-1")).thenReturn(model);
        when(modelService.getClient("m-1")).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder().content(content).build());
    }

    /**
     * 构造一条近邻相似度结果
     */
    private MemorySimilarity neighbor(String id, String content, double similarity) {
        MemorySimilarity memorySimilarity = new MemorySimilarity();
        memorySimilarity.setId(id);
        memorySimilarity.setContent(content);
        memorySimilarity.setSimilarity(similarity);
        return memorySimilarity;
    }

    @Nested
    class ExtractEarlyReturn {

        /** 用户 ID 空白时直接跳过，不访问任何依赖 */
        @Test
        void shouldSkipWhenUserIdBlank() {
            service.extract("  ", application(true), "s-1", message("你好"), message("在的"));

            verifyNoModelInteractions();
        }

        /** 应用为空时直接跳过 */
        @Test
        void shouldSkipWhenApplicationNull() {
            service.extract("u-1", null, "s-1", message("你好"), message("在的"));

            verifyNoModelInteractions();
        }

        /** 用户消息为空时直接跳过 */
        @Test
        void shouldSkipWhenUserMessageNull() {
            service.extract("u-1", application(true), "s-1", null, message("在的"));

            verifyNoModelInteractions();
        }

        /** AI 消息为空时直接跳过 */
        @Test
        void shouldSkipWhenAiMessageNull() {
            service.extract("u-1", application(true), "s-1", message("你好"), null);

            verifyNoModelInteractions();
        }

        /** 应用未启用记忆开关时跳过提取 */
        @Test
        void shouldSkipWhenMemoryDisabled() {
            service.extract("u-1", application(false), "s-1", message("你好"), message("在的"));

            verifyNoModelInteractions();
        }

        private void verifyNoModelInteractions() {
            verify(modelService, never()).getById(anyString());
            verify(modelService, never()).getDefaultModel();
        }
    }

    @Nested
    class ModelResolution {

        /** 应用未绑定模型时回退到默认模型并取默认客户端 */
        @Test
        void shouldFallbackToDefaultModel() {
            ApplicationEntity application = application(true);
            application.setModelId(null);
            ModelEntity defaultModel = new ModelEntity();
            defaultModel.setId("m-default");
            defaultModel.setName("默认模型");
            when(modelService.getDefaultModel()).thenReturn(defaultModel);
            when(modelService.getClient("m-default")).thenReturn(client);
            when(client.chat(any())).thenReturn(
                    ChatResponse.builder().content("{\"memories\":[]}").build());

            service.extract("u-1", application, "s-1", message("你好"), message("在的"));

            verify(modelService).getDefaultModel();
            verify(modelService).getClient("m-default");
        }

        /** 模型响应为 null 时安全返回 */
        @Test
        void shouldReturnWhenResponseNull() {
            ModelEntity model = new ModelEntity();
            model.setId("m-1");
            when(modelService.getById("m-1")).thenReturn(model);
            when(modelService.getClient("m-1")).thenReturn(client);
            when(client.chat(any())).thenReturn(null);

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** 模型正文为空白时安全返回 */
        @Test
        void shouldReturnWhenResponseContentBlank() {
            ModelEntity model = new ModelEntity();
            model.setId("m-1");
            when(modelService.getById("m-1")).thenReturn(model);
            when(modelService.getClient("m-1")).thenReturn(client);
            when(client.chat(any())).thenReturn(ChatResponse.builder().content("  ").build());

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** 模型客户端调用抛异常时容错，不向上抛出 */
        @Test
        void shouldSwallowClientException() {
            ModelEntity model = new ModelEntity();
            model.setId("m-1");
            when(modelService.getById("m-1")).thenReturn(model);
            when(modelService.getClient("m-1")).thenReturn(client);
            when(client.chat(any())).thenThrow(new RuntimeException("网络异常"));

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));
        }
    }

    @Nested
    class ParseAndSave {

        /** 输出不含 JSON 结构时不产生任何入库 */
        @Test
        void shouldIgnoreOutputWithoutJson() {
            modelFlowReturns("没有结构化内容");

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** 兼容被解释性文字包裹的 JSON：提取首 { 到末 } 之间的内容 */
        @Test
        void shouldExtractWrappedJsonAndInsert() {
            modelFlowReturns("分析结果：\n"
                    + "{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式咖啡\",\"confidence\":0.9}]}\n以上。");
            when(longTermMemoryService.embed("喜欢美式咖啡")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt())).thenReturn(List.of());

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));

            verify(longTermMemoryService).insertNew(any(LongTermMemoryEntity.class), any());
        }

        /** 非法 JSON 解析失败时容错，不产生入库 */
        @Test
        void shouldTolerateInvalidJson() {
            modelFlowReturns("{这不是合法 JSON}");

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** memories 为空数组时不产生入库 */
        @Test
        void shouldSkipWhenMemoriesEmpty() {
            modelFlowReturns("{\"memories\":[]}");

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** 单条记忆维度或内容空白时跳过该条 */
        @Test
        void shouldSkipItemsWithBlankDimensionOrContent() {
            modelFlowReturns("{\"memories\":["
                    + "{\"dimension\":\"\",\"memory_type\":\"user\",\"content\":\"内容\",\"confidence\":0.9},"
                    + "{\"dimension\":\"preference\",\"memory_type\":\"user\",\"content\":\"\",\"confidence\":0.9}]}");

            service.extract("u-1", application(true), "s-1", message("你好"), message("在的"));

            verify(longTermMemoryService, never()).embed(anyString());
        }

        /** confidence 缺失时回落到 0.8 */
        @Test
        void shouldDefaultConfidenceWhenMissing() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式\",\"confidence\":null}]}");
            when(longTermMemoryService.embed("喜欢美式")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt())).thenReturn(List.of());

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));

            verify(longTermMemoryService).insertNew(any(LongTermMemoryEntity.class), any());
        }

        /** scene 类型记忆写入会话 ID，user 类型不写会话 ID */
        @Test
        void shouldSetSessionIdForSceneButNotForUser() {
            modelFlowReturns("{\"memories\":["
                    + "{\"dimension\":\"goal\",\"memory_type\":\"scene\",\"content\":\"本次任务约定\",\"confidence\":0.9},"
                    + "{\"dimension\":\"background\",\"memory_type\":\"user\",\"content\":\"是工程师\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt())).thenReturn(List.of());

            service.extract("u-1", application(true), "s-1", message("约定"), message("好的"));

            org.mockito.ArgumentCaptor<LongTermMemoryEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(LongTermMemoryEntity.class);
            verify(longTermMemoryService, org.mockito.Mockito.times(2))
                    .insertNew(captor.capture(), any());
            org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
                    .anySatisfy(entity -> {
                        org.assertj.core.api.Assertions.assertThat(entity.getSessionId()).isEqualTo("s-1");
                        org.assertj.core.api.Assertions.assertThat(entity.getMemoryType()).isEqualTo("scene");
                    })
                    .anySatisfy(entity -> {
                        org.assertj.core.api.Assertions.assertThat(entity.getSessionId()).isNull();
                        org.assertj.core.api.Assertions.assertThat(entity.getMemoryType()).isEqualTo("user");
                    });
        }
    }

    @Nested
    class StoreMemory {

        /** 向量嵌入为 null 时走精确去重 upsert 降级路径 */
        @Test
        void shouldUpsertWhenEmbeddingNull() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢美式")).thenReturn(null);

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));

            verify(longTermMemoryService).upsert(any(LongTermMemoryEntity.class));
            verify(longTermMemoryService, never()).findSimilar(any(), any(), anyInt());
        }

        /** 没有近邻时作为新记忆插入 */
        @Test
        void shouldInsertNewWhenNoNeighbors() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢美式")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt())).thenReturn(List.of());

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));

            verify(longTermMemoryService).insertNew(any(LongTermMemoryEntity.class), any());
        }

        /** 最近邻相似度达到重复阈值时只强化已有记忆 */
        @Test
        void shouldReinforceWhenDuplicate() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢美式")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt()))
                    .thenReturn(List.of(neighbor("old-1", "爱喝美式咖啡", 0.95)));

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));

            verify(longTermMemoryService).reinforce("old-1", "inferred");
            verify(longTermMemoryService, never()).insertNew(any(), any());
        }

        /** 相似度落在合并区间且判定可合并时，嵌入合并文本并归并 */
        @Test
        void shouldMergeWhenSimilarEnough() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢冰美式\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢冰美式")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt()))
                    .thenReturn(List.of(neighbor("old-1", "喜欢美式", 0.85)));
            when(memoryMergeService.decideMergedContent(eq(client), anyDouble(),
                    eq("喜欢美式"), eq("喜欢冰美式"),
                    eq("app-1"), eq("助手应用"), eq("m-1"), eq("s-1"), eq("u-1")))
                    .thenReturn("喜欢美式咖啡");
            when(longTermMemoryService.embed("喜欢美式咖啡")).thenReturn(new float[]{0.2f});

            service.extract("u-1", application(true), "s-1", message("我喜欢冰美式"), message("好的"));

            verify(longTermMemoryService).mergeInto(eq("old-1"), eq("喜欢美式咖啡"), anyDouble(), any());
            verify(longTermMemoryService, never()).insertNew(any(), any());
        }

        /** 相似度落在合并区间但判定不应合并时，作为新记忆插入 */
        @Test
        void shouldInsertNewWhenMergeRejected() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢拿铁\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢拿铁")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt()))
                    .thenReturn(List.of(neighbor("old-1", "喜欢美式", 0.85)));
            when(memoryMergeService.decideMergedContent(any(), any(),
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(null);

            service.extract("u-1", application(true), "s-1", message("我喜欢拿铁"), message("好的"));

            verify(longTermMemoryService).insertNew(any(LongTermMemoryEntity.class), any());
            verify(longTermMemoryService, never()).mergeInto(anyString(), anyString(), anyDouble(), any());
        }

        /** 相似度低于合并阈值时直接作为新记忆插入，不调用合并判定 */
        @Test
        void shouldInsertNewWhenDissimilar() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"住在北京\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("住在北京")).thenReturn(new float[]{0.1f});
            when(longTermMemoryService.findSimilar(any(), any(), anyInt()))
                    .thenReturn(List.of(neighbor("old-1", "喜欢美式", 0.3)));

            service.extract("u-1", application(true), "s-1", message("我住北京"), message("好的"));

            verify(longTermMemoryService).insertNew(any(LongTermMemoryEntity.class), any());
            verify(memoryMergeService, never()).decideMergedContent(
                    any(), any(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }

        /** 入库过程中抛异常时容错，不向上抛出 */
        @Test
        void shouldSwallowStoreException() {
            modelFlowReturns("{\"memories\":[{\"dimension\":\"preference\",\"memory_type\":\"user\","
                    + "\"content\":\"喜欢美式\",\"confidence\":0.9}]}");
            when(longTermMemoryService.embed("喜欢美式"))
                    .thenThrow(new RuntimeException("向量服务异常"));

            service.extract("u-1", application(true), "s-1", message("我喜欢美式"), message("好的"));
        }
    }
}
