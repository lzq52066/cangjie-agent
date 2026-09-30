package cn.cangjiecloud.application.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApplicationEntity} 智能应用实体单元测试：覆盖字段读写、继承字段与等值语义。
 */
class ApplicationEntityTest {

    @Nested
    class Accessors {

        @Test
        void settersShouldUpdateBusinessFields() {
            ApplicationEntity entity = new ApplicationEntity();
            entity.setName("客服助手");
            entity.setDescription("智能问答");
            entity.setType("agent");
            entity.setModelId("model-1");
            entity.setKnowledgeBaseIds("[\"kb-1\"]");
            entity.setPromptTemplateId("pt-1");
            entity.setSkillIds("[\"s1\"]");
            entity.setRuleIds("[\"r1\"]");
            entity.setToolIds("[\"t1\"]");
            entity.setMemoryEnabled(true);
            entity.setMaxTurns(30);
            entity.setTemperature(0.4d);
            entity.setConfig("{\"k\":\"v\"}");
            entity.setSuggestions("[\"q1\"]");
            entity.setIcon("robot");
            entity.setStatus("published");
            entity.setRagMode("agentic");
            entity.setApikey("cj_x");
            entity.setTokenQuota(1000L);
            entity.setTokensUsed(50L);

            assertThat(entity.getName()).isEqualTo("客服助手");
            assertThat(entity.getDescription()).isEqualTo("智能问答");
            assertThat(entity.getType()).isEqualTo("agent");
            assertThat(entity.getModelId()).isEqualTo("model-1");
            assertThat(entity.getKnowledgeBaseIds()).isEqualTo("[\"kb-1\"]");
            assertThat(entity.getPromptTemplateId()).isEqualTo("pt-1");
            assertThat(entity.getSkillIds()).isEqualTo("[\"s1\"]");
            assertThat(entity.getRuleIds()).isEqualTo("[\"r1\"]");
            assertThat(entity.getToolIds()).isEqualTo("[\"t1\"]");
            assertThat(entity.getMemoryEnabled()).isTrue();
            assertThat(entity.getMaxTurns()).isEqualTo(30);
            assertThat(entity.getTemperature()).isEqualTo(0.4d);
            assertThat(entity.getConfig()).isEqualTo("{\"k\":\"v\"}");
            assertThat(entity.getSuggestions()).isEqualTo("[\"q1\"]");
            assertThat(entity.getIcon()).isEqualTo("robot");
            assertThat(entity.getStatus()).isEqualTo("published");
            assertThat(entity.getRagMode()).isEqualTo("agentic");
            assertThat(entity.getApikey()).isEqualTo("cj_x");
            assertThat(entity.getTokenQuota()).isEqualTo(1000L);
            assertThat(entity.getTokensUsed()).isEqualTo(50L);
        }

        @Test
        void shouldExposeInheritedBaseFields() {
            ApplicationEntity entity = new ApplicationEntity();
            entity.setId("id-1");
            entity.setCreateBy("admin");
            entity.setUpdateBy("admin");
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo("id-1");
            assertThat(entity.getCreateBy()).isEqualTo("admin");
            assertThat(entity.getUpdateBy()).isEqualTo("admin");
            assertThat(entity.getDeleted()).isZero();
        }
    }

    @Nested
    class Equality {

        @Test
        void equalEntitiesShouldShareHashCodeAndDifferByFields() {
            ApplicationEntity a = new ApplicationEntity();
            a.setId("1");
            a.setName("same");
            ApplicationEntity b = new ApplicationEntity();
            b.setId("1");
            b.setName("same");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);

            b.setName("other");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void shouldNotBeEqualToNullOrDifferentType() {
            ApplicationEntity entity = new ApplicationEntity();
            entity.setId("1");

            assertThat(entity).isNotEqualTo(null);
            assertThat(entity).isNotEqualTo(new ApplicationVersionEntity());
        }

        @Test
        void toStringShouldContainCoreFields() {
            ApplicationEntity entity = new ApplicationEntity();
            entity.setName("demo");
            entity.setType("chat");

            assertThat(entity.toString()).contains("name=demo").contains("type=chat");
        }
    }
}
