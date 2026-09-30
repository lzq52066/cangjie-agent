package cn.cangjiecloud.application.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApplicationVersionEntity} 应用版本快照实体单元测试：覆盖字段读写与等值语义。
 */
class ApplicationVersionEntityTest {

    @Nested
    class Accessors {

        @Test
        void settersShouldUpdateAllFields() {
            ApplicationVersionEntity entity = new ApplicationVersionEntity();
            entity.setApplicationId("app-1");
            entity.setVersion(5);
            entity.setName("v5 应用");
            entity.setDescription("版本描述");
            entity.setType("agent");
            entity.setModelId("model-2");
            entity.setKnowledgeBaseIds("[]");
            entity.setPromptTemplateId("pt-9");
            entity.setSkillIds("[]");
            entity.setRuleIds("[]");
            entity.setMemoryEnabled(true);
            entity.setMaxTurns(15);
            entity.setTemperature(0.9d);
            entity.setConfig("{}");
            entity.setSuggestions("[]");
            entity.setIcon("star");
            entity.setSnapshot("{\"id\":\"app-1\"}");
            entity.setPublishLog("发布版本 v5");
            entity.setPublishBy("alice");

            assertThat(entity.getApplicationId()).isEqualTo("app-1");
            assertThat(entity.getVersion()).isEqualTo(5);
            assertThat(entity.getName()).isEqualTo("v5 应用");
            assertThat(entity.getDescription()).isEqualTo("版本描述");
            assertThat(entity.getType()).isEqualTo("agent");
            assertThat(entity.getModelId()).isEqualTo("model-2");
            assertThat(entity.getKnowledgeBaseIds()).isEqualTo("[]");
            assertThat(entity.getPromptTemplateId()).isEqualTo("pt-9");
            assertThat(entity.getSkillIds()).isEqualTo("[]");
            assertThat(entity.getRuleIds()).isEqualTo("[]");
            assertThat(entity.getMemoryEnabled()).isTrue();
            assertThat(entity.getMaxTurns()).isEqualTo(15);
            assertThat(entity.getTemperature()).isEqualTo(0.9d);
            assertThat(entity.getConfig()).isEqualTo("{}");
            assertThat(entity.getSuggestions()).isEqualTo("[]");
            assertThat(entity.getIcon()).isEqualTo("star");
            assertThat(entity.getSnapshot()).isEqualTo("{\"id\":\"app-1\"}");
            assertThat(entity.getPublishLog()).isEqualTo("发布版本 v5");
            assertThat(entity.getPublishBy()).isEqualTo("alice");
        }
    }

    @Nested
    class Equality {

        @Test
        void equalVersionsShouldShareHashCode() {
            ApplicationVersionEntity a = new ApplicationVersionEntity();
            a.setId("v1");
            a.setApplicationId("app-1");
            ApplicationVersionEntity b = new ApplicationVersionEntity();
            b.setId("v1");
            b.setApplicationId("app-1");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);

            b.setVersion(2);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void shouldNotBeEqualToNullOrDifferentType() {
            ApplicationVersionEntity entity = new ApplicationVersionEntity();
            entity.setId("1");

            assertThat(entity).isNotEqualTo(null);
            assertThat(entity).isNotEqualTo(new ApplicationEntity());
        }

        @Test
        void toStringShouldContainCoreFields() {
            ApplicationVersionEntity entity = new ApplicationVersionEntity();
            entity.setApplicationId("app-1");
            entity.setVersion(7);

            assertThat(entity.toString())
                    .contains("applicationId=app-1").contains("version=7");
        }
    }
}
