package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DescriptionContributor} 单元测试：仅在模板/检索均无人设时以应用描述兜底。
 */
class DescriptionContributorTest {

    private final DescriptionContributor contributor = new DescriptionContributor();

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        attributes = new HashMap<>();
        attributes.put(ContextAttrs.DESCRIPTION, "我是一个智能助手");
    }

    private ContextRequest request() {
        return ContextRequest.builder().attributes(attributes).build();
    }

    @Test
    void metadataShouldDeclareSystemSlotAndOrder260() {
        assertThat(contributor.name()).isEqualTo("applicationDescription");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.SYSTEM);
        assertThat(contributor.getOrder()).isEqualTo(260);
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportWhenDescriptionIsNonBlankString() {
            assertThat(contributor.supports(request())).isTrue();
        }

        @Test
        void shouldNotSupportWhenDescriptionMissing() {
            attributes.remove(ContextAttrs.DESCRIPTION);
            assertThat(contributor.supports(request())).isFalse();
        }

        @Test
        void shouldNotSupportWhenDescriptionIsBlank() {
            attributes.put(ContextAttrs.DESCRIPTION, "   ");
            assertThat(contributor.supports(request())).isFalse();
        }
    }

    @Nested
    class Contribute {

        @Test
        void shouldEmitDescriptionWhenNoPersonaFragmentProduced() {
            attributes.put(ContextAttrs.FRAGMENTS, List.of());

            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.isEmpty()).isFalse();
            assertThat(fragment.getMessages()).hasSize(1);
            assertThat(fragment.getMessages().get(0).getContent()).isEqualTo("我是一个智能助手");
        }

        @Test
        void shouldReturnEmptyWhenPromptTemplateFragmentAlreadyProduced() {
            attributes.put(ContextAttrs.FRAGMENTS,
                    List.of(ContextFragment.of(ContextSlot.SYSTEM, "promptTemplate", List.of())));

            assertThat(contributor.contribute(request()).isEmpty()).isTrue();
        }

        @Test
        void shouldReturnEmptyWhenRagFragmentAlreadyProduced() {
            attributes.put(ContextAttrs.FRAGMENTS,
                    List.of(ContextFragment.of(ContextSlot.RAG, "rag", List.of())));

            assertThat(contributor.contribute(request()).isEmpty()).isTrue();
        }

        @Test
        void shouldEmitDescriptionWhenOnlySkillOrRuleFragmentsExist() {
            attributes.put(ContextAttrs.FRAGMENTS, List.of(
                    ContextFragment.of(ContextSlot.SYSTEM, "skill", List.of()),
                    ContextFragment.of(ContextSlot.SYSTEM, "rule", List.of())));

            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.isEmpty()).isFalse();
            assertThat(fragment.getMessages().get(0).getContent()).isEqualTo("我是一个智能助手");
        }

        @Test
        void shouldTreatMissingFragmentsAttributeAsNoPersona() {
            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.isEmpty()).isFalse();
        }
    }
}
