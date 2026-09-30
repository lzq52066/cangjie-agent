package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.context.ContextBudget;
import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextResult;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultContextPipeline} 单元测试：贡献者按调度序执行、SYSTEM 槽位片段合并、
 * 单贡献者异常容错、预算应用与最终按槽位装配。
 */
class DefaultContextPipelineTest {

    private HarnessConfigResolver configResolver;

    @BeforeEach
    void setUp() {
        configResolver = mock(HarnessConfigResolver.class);
        when(configResolver.budget(any())).thenReturn(ContextBudget.disabled());
    }

    private DefaultContextPipeline pipeline(ContextContributor... contributors) {
        return new DefaultContextPipeline(List.of(contributors), configResolver);
    }

    private static ContextContributor stub(String name, ContextSlot slot, int order,
                                           boolean supports, ContextFragment fragment) {
        ContextContributor contributor = mock(ContextContributor.class);
        when(contributor.name()).thenReturn(name);
        when(contributor.slot()).thenReturn(slot);
        when(contributor.getOrder()).thenReturn(order);
        when(contributor.supports(any())).thenReturn(supports);
        when(contributor.contribute(any())).thenReturn(fragment);
        return contributor;
    }

    private static ContextFragment systemFragment(String source, String content) {
        return ContextFragment.of(ContextSlot.SYSTEM, source, List.of(ChatMessage.system(content)));
    }

    private ContextRequest request() {
        return ContextRequest.builder().build();
    }

    @Test
    void shouldExecuteContributorsInOrderRegardlessOfRegistrationOrder() {
        ContextFragment first = ContextFragment.of(ContextSlot.HISTORY, "history",
                List.of(ChatMessage.assistant("a")));
        ContextFragment second = ContextFragment.of(ContextSlot.USER, "userMessage",
                List.of(ChatMessage.user("b")));
        ContextContributor high = stub("userMessage", ContextSlot.USER, 650, true, second);
        ContextContributor low = stub("history", ContextSlot.HISTORY, 150, true, first);

        ContextResult result = pipeline(high, low).assemble(request());

        assertThat(result.getFragments()).extracting(ContextFragment::getSource)
                .containsExactly("history", "userMessage");
    }

    @Test
    void shouldMergeSystemFragmentsIntoOneSystemMessage() {
        ContextContributor template = stub("promptTemplate", ContextSlot.SYSTEM, 100, true,
                systemFragment("promptTemplate", "你是助手"));
        ContextContributor skill = stub("skill", ContextSlot.SYSTEM, 120, true,
                systemFragment("skill", "使用技能时保持严谨"));

        ContextResult result = pipeline(template, skill).assemble(request());

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(result.getMessages().get(0).getContent())
                .isEqualTo("你是助手\n\n使用技能时保持严谨");
    }

    @Test
    void shouldSkipUnsupportedContributors() {
        ContextContributor skipped = stub("x", ContextSlot.USER, 100, false, null);

        ContextResult result = pipeline(skipped).assemble(request());

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getFragments()).isEmpty();
    }

    @Test
    void shouldSkipNullAndEmptyFragments() {
        ContextContributor empty = stub("empty", ContextSlot.USER, 100, true,
                ContextFragment.empty(ContextSlot.USER, "empty"));

        ContextResult result = pipeline(empty).assemble(request());

        assertThat(result.getFragments()).isEmpty();
    }

    @Test
    void shouldTolerateContributorExceptionAndContinuePipeline() {
        ContextContributor broken = stub("broken", ContextSlot.SYSTEM, 100, true, null);
        when(broken.contribute(any())).thenThrow(new RuntimeException("boom"));
        ContextContributor healthy = stub("userMessage", ContextSlot.USER, 650, true,
                ContextFragment.of(ContextSlot.USER, "userMessage",
                        List.of(ChatMessage.user("仍然生效"))));

        ContextResult result = pipeline(broken, healthy).assemble(request());

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("仍然生效");
    }

    @Test
    void shouldInitializeAttributesWhenNullAndShareProducedFragments() {
        ContextRequest request = ContextRequest.builder().build();
        ContextContributor contributor = stub("userMessage", ContextSlot.USER, 650, true,
                ContextFragment.of(ContextSlot.USER, "userMessage",
                        List.of(ChatMessage.user("hi"))));

        pipeline(contributor).assemble(request);

        assertThat(request.getAttributes()).containsKey(ContextAttrs.FRAGMENTS);
    }

    @Nested
    class AssemblyOrder {

        @Test
        void shouldAssembleMessagesBySlotOrderWithNonSystemMessagesKeptSeparate() {
            ContextContributor summary = stub("sessionSummary", ContextSlot.SYSTEM_PRE, 50, true,
                    ContextFragment.of(ContextSlot.SYSTEM_PRE, "sessionSummary",
                            List.of(ChatMessage.system("早前摘要"))));
            ContextContributor history = stub("history", ContextSlot.HISTORY, 150, true,
                    ContextFragment.of(ContextSlot.HISTORY, "history",
                            List.of(ChatMessage.user("历史问题"))));
            ContextContributor user = stub("userMessage", ContextSlot.USER, 650, true,
                    ContextFragment.of(ContextSlot.USER, "userMessage",
                            List.of(ChatMessage.user("当前问题"))));

            ContextResult result = pipeline(history, user, summary).assemble(request());

            assertThat(result.getMessages()).extracting(ChatMessage::getRole)
                    .containsExactly("system", "user", "user");
            assertThat(result.getMessages()).extracting(ChatMessage::getContent)
                    .containsExactly("早前摘要", "历史问题", "当前问题");
        }
    }

    @Test
    void shouldReportTrimmedWhenFragmentHasDroppedTokens() {
        ContextFragment fragment = ContextFragment.of(ContextSlot.RAG, "rag",
                List.of(ChatMessage.system("内容")));
        fragment.setDroppedTokens(5);
        ContextContributor rag = stub("rag", ContextSlot.RAG, 200, true, fragment);

        ContextResult result = pipeline(rag).assemble(request());

        assertThat(result.isTrimmed()).isTrue();
        assertThat(result.getEstTokens()).isPositive();
    }
}
