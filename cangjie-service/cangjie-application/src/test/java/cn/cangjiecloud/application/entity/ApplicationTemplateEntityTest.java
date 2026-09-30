package cn.cangjiecloud.application.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApplicationTemplateEntity} 应用模板实体单元测试：覆盖字段读写与等值语义。
 */
class ApplicationTemplateEntityTest {

    @Nested
    class Accessors {

        @Test
        void settersShouldUpdateAllFields() {
            ApplicationTemplateEntity entity = new ApplicationTemplateEntity();
            entity.setName("模板");
            entity.setDescription("描述");
            entity.setIcon("icon");
            entity.setCategory("客服");
            entity.setAppType("chat");
            entity.setSnapshot("{\"application\":{}}");
            entity.setUseCount(3);
            entity.setStatus("published");
            entity.setTemplateKey("tpl-1");
            entity.setVersion(2);
            entity.setBuiltin(true);

            assertThat(entity.getName()).isEqualTo("模板");
            assertThat(entity.getDescription()).isEqualTo("描述");
            assertThat(entity.getIcon()).isEqualTo("icon");
            assertThat(entity.getCategory()).isEqualTo("客服");
            assertThat(entity.getAppType()).isEqualTo("chat");
            assertThat(entity.getSnapshot()).isEqualTo("{\"application\":{}}");
            assertThat(entity.getUseCount()).isEqualTo(3);
            assertThat(entity.getStatus()).isEqualTo("published");
            assertThat(entity.getTemplateKey()).isEqualTo("tpl-1");
            assertThat(entity.getVersion()).isEqualTo(2);
            assertThat(entity.getBuiltin()).isTrue();
        }
    }

    @Nested
    class Equality {

        @Test
        void equalTemplatesShouldShareHashCode() {
            ApplicationTemplateEntity a = new ApplicationTemplateEntity();
            a.setId("1");
            a.setTemplateKey("k");
            ApplicationTemplateEntity b = new ApplicationTemplateEntity();
            b.setId("1");
            b.setTemplateKey("k");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);

            b.setTemplateKey("other");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void shouldNotBeEqualToNullOrDifferentType() {
            ApplicationTemplateEntity entity = new ApplicationTemplateEntity();
            entity.setId("1");

            assertThat(entity).isNotEqualTo(null);
            assertThat(entity).isNotEqualTo(new ApplicationEntity());
        }

        @Test
        void toStringShouldContainCoreFields() {
            ApplicationTemplateEntity entity = new ApplicationTemplateEntity();
            entity.setName("demo-template");
            entity.setStatus("offline");

            assertThat(entity.toString())
                    .contains("name=demo-template").contains("status=offline");
        }
    }
}
