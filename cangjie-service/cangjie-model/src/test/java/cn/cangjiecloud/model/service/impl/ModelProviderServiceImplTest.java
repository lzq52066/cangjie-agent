package cn.cangjiecloud.model.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.model.api.dto.ModelProviderCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelProviderUpdateDTO;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.mapper.ModelProviderMapper;
import cn.cangjiecloud.model.security.ApiKeyCipher;
import cn.cangjiecloud.model.service.IModelService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link ModelProviderServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：预注册实体 TableInfo 使 LambdaWrapper 可离线解析列名；
 * spy 拦截继承自 MyBatis-Plus 的 save/getById/updateById/removeById/page 等方法，
 * ApiKeyCipher、ModelMapper、IModelService 全部使用 mock，
 * 验证厂商标识规范化、唯一性校验、凭证加密/脱敏与缓存失效联动。
 */
class ModelProviderServiceImplTest {

    private ApiKeyCipher apiKeyCipher;
    private ModelMapper modelMapper;
    private IModelService modelService;
    private ModelProviderMapper providerMapper;
    private ModelProviderServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ModelProviderMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ModelProviderEntity.class);
    }

    @BeforeEach
    void setUp() {
        apiKeyCipher = mock(ApiKeyCipher.class);
        modelMapper = mock(ModelMapper.class);
        modelService = mock(IModelService.class);
        providerMapper = mock(ModelProviderMapper.class);

        service = spy(new ModelProviderServiceImpl(apiKeyCipher, modelMapper, modelService));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", providerMapper);

        // 链式查询在 3.5.9 中先解析实体/ mapper 类型，直接打桩避免走真实 MyBatis 代理
        doReturn(ModelProviderEntity.class).when(service).getEntityClass();
        doReturn(ModelProviderMapper.class).when(service).getMapperClass();
        org.mockito.Mockito.when(providerMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        // 默认无重名厂商
        doReturn(0L).when(service).count(any(Wrapper.class));
        org.mockito.Mockito.when(apiKeyCipher.encrypt(any())).thenReturn("ENC");
        // null 密钥脱敏后仍为 null，避免字符串拼接把 null 变成 "****null"
        org.mockito.Mockito.when(apiKeyCipher.mask(any())).thenAnswer(inv ->
                inv.getArgument(0) == null ? null : "****" + inv.getArgument(0));
    }

    private static ModelProviderCreateDTO createDTO(String name, String code, String apiKey) {
        ModelProviderCreateDTO dto = new ModelProviderCreateDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setBaseUrl("https://example.com/v1");
        dto.setApiKey(apiKey);
        dto.setDescription("描述");
        return dto;
    }

    private static ModelProviderEntity provider(String id, String code) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId(id);
        entity.setName("厂商");
        entity.setCode(code);
        entity.setBaseUrl("https://example.com/v1");
        entity.setApiKey("ENC");
        entity.setStatus("active");
        return entity;
    }

    // ---------- create ----------

    @Nested
    class Create {

        @Test
        void shouldNormalizeCodeEncryptKeyAndReturnMasked() {
            ModelProviderEntity result = service.create(createDTO("厂商", " OpenAI ", "raw-key"));

            ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
            verify(service).save(captor.capture());
            ModelProviderEntity saved = captor.getValue();
            // code 被 trim + 转小写
            assertThat(saved.getCode()).isEqualTo("openai");
            assertThat(saved.getStatus()).isEqualTo("active");
            // 空白 baseUrl 被 trim，非空保留
            assertThat(saved.getBaseUrl()).isEqualTo("https://example.com/v1");
            // 持久化前完成加密（saved 与返回值为同一实例，随后被 masked() 就地脱敏）
            verify(apiKeyCipher).encrypt("raw-key");
            assertThat(result.getApiKey()).isEqualTo("****ENC");
            assertThat(saved.getApiKey()).isEqualTo("****ENC");
        }

        @Test
        void shouldStoreNullApiKeyWhenBlank() {
            service.create(createDTO("厂商", "openai", "  "));

            ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
            verify(service).save(captor.capture());
            assertThat(captor.getValue().getApiKey()).isNull();
            verify(apiKeyCipher, never()).encrypt(any());
        }

        @Test
        void shouldConvertBlankBaseUrlToNull() {
            ModelProviderCreateDTO dto = createDTO("厂商", "openai", null);
            dto.setBaseUrl("   ");

            service.create(dto);

            ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
            verify(service).save(captor.capture());
            assertThat(captor.getValue().getBaseUrl()).isNull();
        }

        @Test
        void shouldThrowWhenCodeNotUnique() {
            // assertCodeUnique 走 lambdaQuery 链，最终调用 baseMapper.selectCount
            org.mockito.Mockito.when(providerMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("厂商", "openai", "k")),
                    ApiException.class);

            assertThat(ex).hasMessage("厂商标识已存在: openai");
            verify(service, never()).save(any());
        }

        @Test
        void shouldThrowWhenCodeFormatInvalid() {
            ApiException ex = catchThrowableOfType(() -> service.create(createDTO("厂商", "bad code!", "k")),
                    ApiException.class);

            assertThat(ex.getMessage()).contains("厂商标识仅支持");
        }

        @Test
        void shouldThrowWhenCodeTooLong() {
            ApiException ex = catchThrowableOfType(
                    () -> service.create(createDTO("厂商", "a".repeat(21), "k")), ApiException.class);

            assertThat(ex.getMessage()).contains("厂商标识仅支持");
        }
    }

    // ---------- update ----------

    @Nested
    class Update {

        @Test
        void shouldThrowWhenProviderMissing() {
            doReturn(null).when(service).getById("p1");

            ApiException ex = catchThrowableOfType(
                    () -> service.update("p1", new ModelProviderUpdateDTO()), ApiException.class);

            assertThat(ex).hasMessage("厂商不存在");
        }

        @Test
        void shouldApplyNameStatusAndDescriptionWithoutCredentialChange() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            dto.setName("新名称");
            dto.setStatus("inactive");
            dto.setDescription("新描述");

            ModelProviderEntity result = service.update("p1", dto);

            assertThat(result.getName()).isEqualTo("新名称");
            assertThat(result.getStatus()).isEqualTo("inactive");
            assertThat(result.getDescription()).isEqualTo("新描述");
            verify(service).updateById(any());
            verify(modelService, never()).evictClientsOfProvider(any());
        }

        @Test
        void shouldNormalizeAndValidateCodeWhenGiven() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            doReturn(0L).when(service).count(any(Wrapper.class));
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            dto.setCode(" DeepSeek ");

            ModelProviderEntity result = service.update("p1", dto);

            assertThat(result.getCode()).isEqualTo("deepseek");
            // code 变更属于凭证变更，需联动失效继承模型缓存
            verify(modelService).evictClientsOfProvider("p1");
        }

        @Test
        void shouldThrowWhenUpdatedCodeConflicts() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            org.mockito.Mockito.when(providerMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            dto.setCode("deepseek");

            ApiException ex = catchThrowableOfType(() -> service.update("p1", dto), ApiException.class);

            assertThat(ex).hasMessage("厂商标识已存在: deepseek");
        }

        @Test
        void shouldEncryptNewApiKeyWhenValueContainsNoMask() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            dto.setApiKey("new-raw-key");

            service.update("p1", dto);

            verify(apiKeyCipher).encrypt("new-raw-key");
            verify(modelService).evictClientsOfProvider("p1");
        }

        @Test
        void shouldIgnoreMaskedApiKeyEchoedFromFrontend() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            // 含掩码符的 apiKey 是前端回传的脱敏值，不能覆盖真实密钥
            dto.setApiKey("****ENC");

            // updateById 之后同一实体会被 masked() 就地脱敏，需在调用发生时快照密文
            String[] persistedKey = new String[1];
            org.mockito.Mockito.doAnswer(inv -> {
                persistedKey[0] = inv.<ModelProviderEntity>getArgument(0).getApiKey();
                return true;
            }).when(service).updateById(any(ModelProviderEntity.class));

            ModelProviderEntity result = service.update("p1", dto);

            // 返回值统一脱敏，故断言脱敏结果；同时确认底层密文未被掩码值覆盖
            assertThat(result.getApiKey()).isEqualTo("****ENC");
            assertThat(persistedKey[0]).isEqualTo("ENC");
            verify(apiKeyCipher, never()).encrypt(any());
            verify(modelService, never()).evictClientsOfProvider(any());
        }

        @Test
        void shouldDetectBaseUrlChangeAndEvictCache() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            dto.setBaseUrl("https://new.example.com/v1");

            service.update("p1", dto);

            verify(modelService).evictClientsOfProvider("p1");
        }

        @Test
        void shouldReturnMaskedEntity() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");

            ModelProviderEntity result = service.update("p1", new ModelProviderUpdateDTO());

            assertThat(result.getApiKey()).startsWith("****");
        }
    }

    // ---------- delete ----------

    @Nested
    class Delete {

        @Test
        void shouldDoNothingWhenProviderMissing() {
            doReturn(null).when(service).getById("p1");

            service.delete("p1");

            verify(service, never()).removeById(any(java.io.Serializable.class));
        }

        @Test
        void shouldThrowWhenProviderStillUsedByModels() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            org.mockito.Mockito.when(modelMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

            ApiException ex = catchThrowableOfType(() -> service.delete("p1"), ApiException.class);

            assertThat(ex.getMessage()).contains("仍有 3 个模型");
            verify(service, never()).removeById(any(java.io.Serializable.class));
        }

        @Test
        void shouldRemoveAndEvictCacheWhenNoModelUsed() {
            doReturn(provider("p1", "openai")).when(service).getById("p1");
            org.mockito.Mockito.when(modelMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

            service.delete("p1");

            verify(service).removeById("p1");
            verify(modelService).evictClientsOfProvider("p1");
        }
    }

    // ---------- pageQuery ----------

    @Nested
    class PageQuery {

        @Test
        void shouldQueryWithKeywordAndStatusAndOrderByCode() {
            Page<ModelProviderEntity> rawPage = new Page<>(1, 10);
            doReturn(rawPage).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ModelProviderEntity> result = service.pageQuery("open", "active", 1, 10);

            ArgumentCaptor<Wrapper<ModelProviderEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            String sql = captor.getValue().getTargetSql();
            assertThat(sql).contains("name LIKE").contains("code LIKE").contains("status =");
            assertThat(sql).containsIgnoringCase("ORDER BY code ASC");
            // 返回的分页对象经过 convert 脱敏
            assertThat(result).isNotNull();
        }

        @Test
        void shouldSkipFiltersWhenConditionsBlank() {
            doReturn(new Page<ModelProviderEntity>(1, 10)).when(service).page(any(Page.class), any(Wrapper.class));

            service.pageQuery("  ", null, null, null);

            ArgumentCaptor<Wrapper<ModelProviderEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            assertThat(captor.getValue().getTargetSql()).doesNotContain("LIKE").doesNotContain("=");
        }

        @Test
        void shouldMaskEveryRecordOnPage() {
            Page<ModelProviderEntity> rawPage = new Page<>(1, 10);
            rawPage.setRecords(java.util.List.of(provider("p1", "openai"), provider("p2", "deepseek")));
            doReturn(rawPage).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ModelProviderEntity> result = service.pageQuery(null, null, 1, 10);

            assertThat(result.getRecords()).allSatisfy(e -> assertThat(e.getApiKey()).startsWith("****"));
        }
    }
}
