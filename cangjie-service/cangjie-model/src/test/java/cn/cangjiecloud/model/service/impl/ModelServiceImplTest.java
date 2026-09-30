package cn.cangjiecloud.model.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.mapper.ModelProviderMapper;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.security.ApiKeyCipher;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ModelServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：预注册实体 TableInfo 使 LambdaWrapper 可离线解析列名；
 * spy 拦截继承自 MyBatis-Plus 的 getById/save/updateById/removeById/page/getOne 等方法，
 * ModelProviderMapper、熔断器、加解密器、流式执行器全部使用 mock。
 */
class ModelServiceImplTest {

    private ModelProviderMapper providerMapper;
    private ModelMapper modelMapper;
    private ModelCircuitBreaker circuitBreaker;
    private ApiKeyCipher apiKeyCipher;
    private AsyncTaskExecutor streamExecutor;
    private ModelServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        initTableInfo(ModelMapper.class, ModelEntity.class);
    }

    /** 让 LambdaWrapper 的 SFunction 能离线解析成真实列名 */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        modelMapper = mock(ModelMapper.class);
        providerMapper = mock(ModelProviderMapper.class);
        circuitBreaker = mock(ModelCircuitBreaker.class);
        apiKeyCipher = mock(ApiKeyCipher.class);
        streamExecutor = mock(AsyncTaskExecutor.class);

        service = spy(new ModelServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", modelMapper);
        ReflectionTestUtils.setField(service, "modelProviderMapper", providerMapper);
        ReflectionTestUtils.setField(service, "circuitBreaker", circuitBreaker);
        ReflectionTestUtils.setField(service, "apiKeyCipher", apiKeyCipher);
        ReflectionTestUtils.setField(service, "streamExecutor", streamExecutor);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 30L);
        ReflectionTestUtils.setField(service, "fallbackModelId", "");
        ReflectionTestUtils.setField(service, "maxRetries", 0);
        ReflectionTestUtils.setField(service, "retryDelayMs", 0L);

        org.mockito.Mockito.when(circuitBreaker.allowRequest(any())).thenReturn(true);
    }

    // ---------- 测试数据构造 ----------

    private static ModelProviderEntity readyProvider(String id, String code) {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(id);
        provider.setName("厂商-" + code);
        provider.setCode(code);
        provider.setBaseUrl("https://example.com/v1");
        provider.setApiKey("cipher-key");
        return provider;
    }

    private static ModelCreateDTO createDTO(String providerId) {
        ModelCreateDTO dto = new ModelCreateDTO();
        dto.setName("新模型");
        dto.setProviderId(providerId);
        dto.setModelName("gpt-4o");
        // 可选参数置空，验证默认值填充
        dto.setTemperature(null);
        dto.setMaxTokens(null);
        dto.setTopP(null);
        dto.setIsDefault(null);
        dto.setSupportEmbedding(null);
        return dto;
    }

    private void stubProvider(ModelProviderEntity provider) {
        org.mockito.Mockito.when(providerMapper.selectById(provider.getId())).thenReturn(provider);
        org.mockito.Mockito.when(apiKeyCipher.decrypt("cipher-key")).thenReturn("plain-key");
    }

    private static ModelEntity model(String id, String providerId, String status) {
        ModelEntity entity = new ModelEntity();
        entity.setId(id);
        entity.setName("模型-" + id);
        entity.setProviderId(providerId);
        entity.setModelName("gpt-4o");
        entity.setStatus(status);
        entity.setTemperature(0.7);
        entity.setMaxTokens(1024);
        return entity;
    }

    // ---------- create ----------

    @Nested
    class Create {

        @Test
        void shouldFillDefaultsAndPersistWhenOptionalFieldsNull() {
            stubProvider(readyProvider("p1", "openai"));

            ModelEntity result = service.create(createDTO("p1"));

            assertThat(result.getModelType()).isEqualTo("openai");
            assertThat(result.getProviderId()).isEqualTo("p1");
            assertThat(result.getTemperature()).isEqualTo(0.7);
            assertThat(result.getMaxTokens()).isEqualTo(4096);
            assertThat(result.getTopP()).isEqualTo(1.0);
            assertThat(result.getIsDefault()).isFalse();
            assertThat(result.getSupportEmbedding()).isFalse();
            assertThat(result.getStatus()).isEqualTo("active");
            verify(service).save(result);
            verify(service, never()).lambdaUpdate();
        }

        @Test
        void shouldClearOtherDefaultsWhenMarkedAsDefault() {
            ModelProviderEntity provider = readyProvider("p1", "openai");
            stubProvider(provider);
            ModelCreateDTO dto = createDTO("p1");
            dto.setIsDefault(true);
            // save 时回填生成的主键
            doAnswerFillId("new-id").when(modelMapper).insert(any(ModelEntity.class));

            ModelEntity result = service.create(dto);

            assertThat(result.getIsDefault()).isTrue();
            verify(modelMapper).update(any(), any());
        }

        @Test
        void shouldThrowWhenProviderIdBlank() {
            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("  ")),
                    ApiException.class);

            assertThat(ex).hasMessage("请选择厂商");
            verify(service, never()).save(any());
        }

        @Test
        void shouldThrowWhenProviderNotFound() {
            org.mockito.Mockito.when(providerMapper.selectById("missing")).thenReturn(null);

            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("missing")),
                    ApiException.class);

            assertThat(ex).hasMessage("关联厂商不存在或已删除: missing");
        }

        @Test
        void shouldThrowWhenProviderBaseUrlMissing() {
            ModelProviderEntity provider = readyProvider("p1", "openai");
            provider.setBaseUrl(null);
            org.mockito.Mockito.when(providerMapper.selectById("p1")).thenReturn(provider);

            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("p1")),
                    ApiException.class);

            assertThat(ex.getMessage()).contains("未配置 Base URL");
        }

        @Test
        void shouldThrowWhenProviderApiKeyMissing() {
            ModelProviderEntity provider = readyProvider("p1", "openai");
            provider.setApiKey(null);
            org.mockito.Mockito.when(providerMapper.selectById("p1")).thenReturn(provider);

            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("p1")),
                    ApiException.class);

            assertThat(ex.getMessage()).contains("未配置 API Key");
        }
    }

    // ---------- update ----------

    @Nested
    class Update {

        @Test
        void shouldThrowWhenModelMissing() {
            doReturn(null).when(service).getById("m1");

            ApiException ex = catchThrowableOfType(() -> service.update("m1", new ModelUpdateDTO()),
                    ApiException.class);

            assertThat(ex).hasMessage("模型不存在");
        }

        @Test
        void shouldApplyOnlyProvidedFieldsInPartialUpdate() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            ModelUpdateDTO dto = new ModelUpdateDTO();
            dto.setName("改名后");
            dto.setTemperature(0.2);

            ModelEntity result = service.update("m1", dto);

            assertThat(result.getName()).isEqualTo("改名后");
            assertThat(result.getTemperature()).isEqualTo(0.2);
            // 未提供的字段保持不变
            assertThat(result.getModelName()).isEqualTo("gpt-4o");
            verify(service).updateById(existing);
        }

        @Test
        void shouldSwitchProviderAndModelTypeWhenProviderIdGiven() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            ModelProviderEntity deepseek = readyProvider("p2", "deepseek");
            stubProvider(deepseek);
            ModelUpdateDTO dto = new ModelUpdateDTO();
            dto.setProviderId("p2");

            ModelEntity result = service.update("m1", dto);

            assertThat(result.getProviderId()).isEqualTo("p2");
            assertThat(result.getModelType()).isEqualTo("deepseek");
        }

        @Test
        void shouldClearOtherDefaultsAndMarkDefault() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            ModelUpdateDTO dto = new ModelUpdateDTO();
            dto.setIsDefault(true);

            service.update("m1", dto);

            assertThat(existing.getIsDefault()).isTrue();
            verify(modelMapper).update(any(), any());
        }

        @Test
        void shouldNotClearDefaultsWhenIsDefaultFalseOrNull() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            ModelUpdateDTO dto = new ModelUpdateDTO();
            dto.setIsDefault(false);

            service.update("m1", dto);

            verify(modelMapper, never()).update(any(), any());
        }

        @Test
        void shouldEvictClientCacheAfterUpdate() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            OpenAICompatibleClient cached = mock(OpenAICompatibleClient.class);
            Map<String, OpenAICompatibleClient> cache = getClientCache();
            cache.put("m1", cached);

            service.update("m1", new ModelUpdateDTO());

            assertThat(cache).doesNotContainKey("m1");
        }
    }

    // ---------- delete ----------

    @Nested
    class Delete {

        @Test
        void shouldDoNothingWhenModelMissing() {
            doReturn(null).when(service).getById("m1");

            service.delete("m1");

            verify(service, never()).removeById(any(java.io.Serializable.class));
        }

        @Test
        void shouldRemoveAndEvictCacheWhenModelExists() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");
            Map<String, OpenAICompatibleClient> cache = getClientCache();
            cache.put("m1", mock(OpenAICompatibleClient.class));

            service.delete("m1");

            verify(service).removeById("m1");
            assertThat(cache).isEmpty();
        }
    }

    // ---------- pageQuery ----------

    @Nested
    class PageQuery {

        @Test
        void shouldApplyAllFiltersAndOrdering() {
            Page<ModelEntity> expected = new Page<>(1, 10);
            doReturn(expected).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ModelEntity> result = service.pageQuery("名", "openai", "p1", 1, 10);

            assertThat(result).isSameAs(expected);
            ArgumentCaptor<Wrapper<ModelEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            String sql = captor.getValue().getTargetSql();
            assertThat(sql).contains("name LIKE").contains("model_type =").contains("provider_id =");
            assertThat(sql).containsIgnoringCase("ORDER BY");
        }

        @Test
        void shouldBuildNoFilterWhenConditionsBlankAndUseDefaultPaging() {
            doReturn(new Page<ModelEntity>(1, 10)).when(service).page(any(Page.class), any(Wrapper.class));

            service.pageQuery("  ", null, null, null, null);

            ArgumentCaptor<Wrapper<ModelEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            assertThat(captor.getValue().getTargetSql()).doesNotContain("LIKE").doesNotContain("=");
        }
    }

    // ---------- testModel ----------

    @Nested
    class TestModel {

        @Test
        void shouldThrowWhenModelMissing() {
            doReturn(null).when(service).getById("m1");

            ApiException ex = catchThrowableOfType(() -> service.testModel("m1", "hi"),
                    ApiException.class);

            assertThat(ex).hasMessage("模型不存在");
        }

        @Test
        void shouldThrowWhenModelInactive() {
            doReturn(model("m1", "p1", "inactive")).when(service).getById("m1");

            ApiException ex = catchThrowableOfType(() -> service.testModel("m1", "hi"),
                    ApiException.class);

            assertThat(ex).hasMessage("模型未激活");
        }

        @Test
        void shouldWrapUnderlyingFailureWithFriendlyMessage() {
            // 厂商齐备但底层客户端调用抛错时，统一包装为友好异常（构造本身被 mock，不发起任何网络请求）
            stubProvider(readyProvider("p1", "openai"));
            doReturn(model("m1", "p1", "active")).when(service).getById("m1");

            ApiException ex;
            try (org.mockito.MockedConstruction<OpenAICompatibleClient> mocked =
                         org.mockito.Mockito.mockConstruction(OpenAICompatibleClient.class,
                                 (client, ctx) -> org.mockito.Mockito.when(client.chat(any()))
                                         .thenThrow(new RuntimeException("boom")))) {
                ex = catchThrowableOfType(() -> service.testModel("m1", "hi"), ApiException.class);
            }

            assertThat(ex.getMessage()).isEqualTo("模型测试失败: boom");
        }
    }

    // ---------- 默认模型 / setDefault ----------

    @Nested
    class DefaultModel {

        @Test
        void getDefaultModelShouldDelegateToGetOne() {
            ModelEntity def = model("m1", "p1", "active");
            def.setIsDefault(true);
            doReturn(def).when(service).getOne(any(Wrapper.class));

            assertThat(service.getDefaultModel()).isSameAs(def);

            ArgumentCaptor<Wrapper<ModelEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture());
            assertThat(captor.getValue().getTargetSql()).contains("is_default =").contains("status =");
        }

        @Test
        void setDefaultShouldThrowWhenModelMissing() {
            doReturn(null).when(service).getById("m1");

            ApiException ex = catchThrowableOfType(() -> service.setDefault("m1"), ApiException.class);

            assertThat(ex).hasMessage("模型不存在");
        }

        @Test
        void setDefaultShouldClearOthersAndPersistAndEvictCache() {
            ModelEntity existing = model("m1", "p1", "active");
            doReturn(existing).when(service).getById("m1");

            service.setDefault("m1");

            assertThat(existing.getIsDefault()).isTrue();
            verify(modelMapper).update(any(), any());
            verify(service).updateById(existing);
        }
    }

    // ---------- 客户端缓存 / 熔断降级 ----------

    @Nested
    class ClientResolution {

        @Test
        void getClientShouldCreateAndCacheWhenAllowed() {
            stubProvider(readyProvider("p1", "openai"));
            doReturn(model("m1", "p1", "active")).when(service).getById("m1");

            OpenAICompatibleClient first = service.getClient("m1");
            OpenAICompatibleClient second = service.getClient("m1");

            assertThat(first).isNotNull().isSameAs(second);
            verify(service, times(1)).getById("m1");
        }

        @Test
        void getClientShouldThrowWhenModelMissing() {
            doReturn(null).when(service).getById("m1");

            ApiException ex = catchThrowableOfType(() -> service.getClient("m1"), ApiException.class);

            assertThat(ex).hasMessage("模型不存在: m1");
        }

        @Test
        void getClientShouldFallBackToConfiguredFallbackModelWhenTripped() {
            org.mockito.Mockito.when(circuitBreaker.allowRequest("m1")).thenReturn(false);
            org.mockito.Mockito.when(circuitBreaker.allowRequest("mf")).thenReturn(true);
            doReturn(model("mf", "p1", "active")).when(service).getById("mf");
            stubProvider(readyProvider("p1", "openai"));
            ReflectionTestUtils.setField(service, "fallbackModelId", "mf");

            OpenAICompatibleClient client = service.getClient("m1");

            assertThat(client).isNotNull();
        }

        @Test
        void getClientShouldFallBackToDefaultModelWhenNoFallbackConfigured() {
            ModelEntity def = model("md", "p1", "active");
            org.mockito.Mockito.when(circuitBreaker.allowRequest("m1")).thenReturn(false);
            org.mockito.Mockito.when(circuitBreaker.allowRequest("md")).thenReturn(true);
            doReturn(def).when(service).getOne(any(Wrapper.class));
            stubProvider(readyProvider("p1", "openai"));

            OpenAICompatibleClient client = service.getClient("m1");

            assertThat(client).isNotNull();
        }

        @Test
        void getClientShouldThrowWhenNoFallbackAvailable() {
            org.mockito.Mockito.when(circuitBreaker.allowRequest("m1")).thenReturn(false);
            doReturn(null).when(service).getOne(any(Wrapper.class));

            ApiException ex = catchThrowableOfType(() -> service.getClient("m1"), ApiException.class);

            assertThat(ex.getMessage()).contains("熔断中");
        }

        @Test
        void getDefaultClientShouldThrowWhenNoDefaultModel() {
            doReturn(null).when(service).getOne(any(Wrapper.class));

            ApiException ex = catchThrowableOfType(() -> service.getDefaultClient(), ApiException.class);

            assertThat(ex).hasMessage("未配置默认模型");
        }

        @Test
        void getDefaultClientShouldCreateAndCache() {
            ModelEntity def = model("md", "p1", "active");
            doReturn(def).when(service).getOne(any(Wrapper.class));
            stubProvider(readyProvider("p1", "openai"));

            OpenAICompatibleClient client = service.getDefaultClient();

            assertThat(client).isNotNull();
        }

        @Test
        void evictClientShouldRemoveCachedClient() {
            Map<String, OpenAICompatibleClient> cache = getClientCache();
            cache.put("m1", mock(OpenAICompatibleClient.class));

            service.evictClient("m1");

            assertThat(cache).isEmpty();
        }
    }

    // ---------- evictClientsOfProvider ----------

    @Nested
    class EvictClientsOfProvider {

        @Test
        void shouldDoNothingWhenProviderIdBlank() {
            service.evictClientsOfProvider("  ");

            verify(service, never()).list(any(Wrapper.class));
        }

        @Test
        void shouldEvictEveryModelOfGivenProvider() {
            ModelEntity a = model("m1", "p1", "active");
            ModelEntity b = model("m2", "p1", "active");
            doReturn(List.of(a, b)).when(service).list(any(Wrapper.class));
            Map<String, OpenAICompatibleClient> cache = getClientCache();
            cache.put("m1", mock(OpenAICompatibleClient.class));
            cache.put("m2", mock(OpenAICompatibleClient.class));

            service.evictClientsOfProvider("p1");

            assertThat(cache).isEmpty();
        }
    }

    // ---------- 辅助方法 ----------

    @SuppressWarnings("unchecked")
    private Map<String, OpenAICompatibleClient> getClientCache() {
        return (Map<String, OpenAICompatibleClient>) ReflectionTestUtils.getField(service, "clientCache");
    }

    private static org.mockito.stubbing.Stubber doAnswerFillId(String id) {
        return org.mockito.Mockito.doAnswer(invocation -> {
            ((ModelEntity) invocation.getArgument(0)).setId(id);
            return 1;
        });
    }
}
