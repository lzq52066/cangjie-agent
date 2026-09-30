package cn.cangjiecloud.prompt.service;

import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PromptCacheService} 单元测试。
 * <p>
 * 使用真实的 Caffeine 本地缓存（每个用例新建实例，缓存互不影响），
 * 模板服务为 mock，验证缓存命中、穿透与主动失效逻辑。
 */
@ExtendWith(MockitoExtension.class)
class PromptCacheServiceTest {

    @Mock
    private IPromptTemplateService promptTemplateService;

    private PromptCacheService promptCacheService;

    @BeforeEach
    void setUp() {
        promptCacheService = new PromptCacheService(promptTemplateService);
    }

    private static PromptTemplateEntity template(String id, String content) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setContent(content);
        return entity;
    }

    @Nested
    @DisplayName("getContent：读取与缓存")
    class GetContent {

        @Test
        void 模板ID为空时直接返回空且不查库() {
            assertThat(promptCacheService.getContent(null)).isNull();
            assertThat(promptCacheService.getContent("")).isNull();

            verify(promptTemplateService, times(0)).getById(any());
        }

        @Test
        void 首次读取未命中缓存时查库并写入缓存() {
            when(promptTemplateService.getById("t1")).thenReturn(template("t1", "你是助手"));

            String first = promptCacheService.getContent("t1");
            String second = promptCacheService.getContent("t1");

            assertThat(first).isEqualTo("你是助手");
            assertThat(second).isEqualTo("你是助手");
            // 第二次命中缓存，不再查库
            verify(promptTemplateService, times(1)).getById("t1");
        }

        @Test
        void 模板不存在时返回空且不缓存空值() {
            when(promptTemplateService.getById("t404")).thenReturn(null);

            assertThat(promptCacheService.getContent("t404")).isNull();
            assertThat(promptCacheService.getContent("t404")).isNull();

            // 未缓存空值，因此每次都回查
            verify(promptTemplateService, times(2)).getById("t404");
        }

        @Test
        void 模板内容为空时不写入缓存() {
            when(promptTemplateService.getById("t2")).thenReturn(template("t2", null));

            assertThat(promptCacheService.getContent("t2")).isNull();

            verify(promptTemplateService, times(1)).getById("t2");
        }

        @Test
        void 查库异常时降级返回空不向外抛出() {
            when(promptTemplateService.getById("t3")).thenThrow(new RuntimeException("db down"));

            assertThatCode(() -> assertThat(promptCacheService.getContent("t3")).isNull())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("evict：主动失效")
    class Evict {

        @Test
        void 失效后重新查库并刷新缓存() {
            when(promptTemplateService.getById("t1"))
                    .thenReturn(template("t1", "旧内容"))
                    .thenReturn(template("t1", "新内容"));

            assertThat(promptCacheService.getContent("t1")).isEqualTo("旧内容");

            promptCacheService.evict("t1");

            assertThat(promptCacheService.getContent("t1")).isEqualTo("新内容");
            verify(promptTemplateService, times(2)).getById("t1");
        }

        @Test
        void 失效不存在的KEY或空KEY不抛异常() {
            assertThatCode(() -> promptCacheService.evict(null)).doesNotThrowAnyException();
            assertThatCode(() -> promptCacheService.evict("not-exists")).doesNotThrowAnyException();
        }

        @Test
        void 失效仅影响指定模板() {
            when(promptTemplateService.getById("t1")).thenReturn(template("t1", "内容1"));
            when(promptTemplateService.getById("t2")).thenReturn(template("t2", "内容2"));

            promptCacheService.getContent("t1");
            promptCacheService.getContent("t2");
            promptCacheService.evict("t1");

            assertThat(promptCacheService.getContent("t2")).isEqualTo("内容2");
            verify(promptTemplateService, times(1)).getById("t2");
        }
    }
}
