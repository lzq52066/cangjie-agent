package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.rule.RuleEvaluationResult;
import cn.cangjiecloud.prompt.rule.RuleEvaluator;
import cn.cangjiecloud.prompt.service.IRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RuleContributor} 单元测试：按规则 ID 加载、过滤 active、编译命中指令。
 */
class RuleContributorTest {

    private IRuleService ruleService;
    private RuleEvaluator ruleEvaluator;
    private RuleContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        ruleService = mock(IRuleService.class);
        ruleEvaluator = mock(RuleEvaluator.class);
        contributor = new RuleContributor(ruleService, ruleEvaluator);
        attributes = new HashMap<>();
    }

    private ContextRequest request() {
        return ContextRequest.builder().applicationId("app-1").userQuery("帮我删库")
                .attributes(attributes).build();
    }

    private static RuleEntity rule(String status) {
        RuleEntity entity = new RuleEntity();
        entity.setId("rule-1");
        entity.setStatus(status);
        return entity;
    }

    @Test
    void metadataShouldDeclareSystemSlotAndOrder130() {
        assertThat(contributor.name()).isEqualTo("rule");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.SYSTEM);
        assertThat(contributor.getOrder()).isEqualTo(130);
    }

    @Test
    void shouldReturnEmptyWhenRuleIdsMissing() {
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveRules() {
        attributes.put(ContextAttrs.RULE_IDS, List.of("rule-1"));
        when(ruleService.listByIds(any())).thenReturn(List.of(rule("inactive")));

        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenEvaluatorCompilesBlankInstructions() {
        attributes.put(ContextAttrs.RULE_IDS, List.of("rule-1"));
        when(ruleService.listByIds(any())).thenReturn(List.of(rule("active")));
        when(ruleEvaluator.evaluate(any(), anyString(), any())).thenReturn(List.of());
        when(ruleEvaluator.compileInstructions(any())).thenReturn("");

        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldProduceSystemFragmentWithCompiledInstructions() {
        attributes.put(ContextAttrs.RULE_IDS, List.of("rule-1"));
        attributes.put(ContextAttrs.APPLICATION_NAME, "运维助手");
        when(ruleService.listByIds(any())).thenReturn(List.of(rule("active")));
        RuleEvaluationResult matched = RuleEvaluationResult.builder().matched(true).build();
        when(ruleEvaluator.evaluate(any(), anyString(), any())).thenReturn(List.of(matched));
        when(ruleEvaluator.compileInstructions(any())).thenReturn("【行为规则】删除前必须二次确认");

        ContextFragment fragment = contributor.contribute(request());

        assertThat(fragment.getSlot()).isEqualTo(ContextSlot.SYSTEM);
        assertThat(fragment.getSource()).isEqualTo("rule");
        assertThat(fragment.getMessages().get(0).getContent())
                .isEqualTo("【行为规则】删除前必须二次确认");
    }
}
