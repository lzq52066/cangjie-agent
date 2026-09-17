package cn.cangjiecloud.core.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WorkflowContext} 单元测试。
 * <p>
 * 该类只有 {@code @Data} 与三个字段（variables 带字段初始化器），无自定义方法，
 * 因此重点覆盖默认容器行为、变量存取语义与 equals/hashCode/toString 契约。
 */
class WorkflowContextTest {

    @Test
    void newInstanceShouldHaveEmptyMutableVariablesAndNullInputOutputs() {
        WorkflowContext context = new WorkflowContext();

        assertThat(context.getExecutionId()).isNull();
        assertThat(context.getVariables()).isNotNull().isEmpty();
        assertThat(context.getInputs()).isNull();
        assertThat(context.getOutputs()).isNull();
    }

    @Test
    void variablesShouldBeSharedInstanceAndAcceptPutGetRemove() {
        WorkflowContext context = new WorkflowContext();
        Map<String, Object> same = context.getVariables();

        same.put("answer", 42);
        context.getVariables().put("flag", true);

        assertThat(context.getVariables()).containsOnlyKeys("answer", "flag")
                .containsEntry("answer", 42)
                .containsEntry("flag", Boolean.TRUE);

        assertThat(context.getVariables().remove("answer")).isEqualTo(42);
        assertThat(context.getVariables()).hasSize(1);
        assertThat(context.getVariables()).doesNotContainKey("answer");
    }

    @Test
    void eachInstanceShouldHaveItsOwnVariablesMap() {
        WorkflowContext a = new WorkflowContext();
        WorkflowContext b = new WorkflowContext();

        a.getVariables().put("k", "v");

        assertThat(b.getVariables()).doesNotContainKey("k");
        assertThat(a.getVariables()).isNotSameAs(b.getVariables());
    }

    @Test
    void settersShouldReplaceContainersIncludingWithNull() {
        WorkflowContext context = new WorkflowContext();
        context.setExecutionId("exec-1");
        context.setVariables(null);
        context.setInputs(Map.of("in", 1));
        context.setOutputs(new LinkedHashMap<>());

        assertThat(context.getExecutionId()).isEqualTo("exec-1");
        assertThat(context.getVariables()).isNull();
        assertThat(context.getInputs()).containsEntry("in", 1);
        assertThat(context.getOutputs()).isEmpty();
    }

    @Test
    void variablesCanBeReplacedByCustomMapInstance() {
        WorkflowContext context = new WorkflowContext();
        Map<String, Object> backing = new HashMap<>();
        backing.put("a", "b");
        context.setVariables(backing);

        assertThat(context.getVariables()).isSameAs(backing);
        context.getVariables().put("c", "d");
        assertThat(backing).containsKey("c");
    }

    @Test
    void equalsAndHashCodeShouldCompareAllThreeFields() {
        WorkflowContext a = context("e1", Map.of("k", 1), Map.of("i", 2), Map.of("o", 3));
        WorkflowContext b = context("e1", Map.of("k", 1), Map.of("i", 2), Map.of("o", 3));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(context("e2", Map.of("k", 1), Map.of("i", 2), Map.of("o", 3)));
        assertThat(a).isNotEqualTo(context("e1", Map.of("k", 9), Map.of("i", 2), Map.of("o", 3)));
        assertThat(a).isNotEqualTo(context("e1", Map.of("k", 1), Map.of("i", 9), Map.of("o", 3)));
        assertThat(a).isNotEqualTo(context("e1", Map.of("k", 1), Map.of("i", 2), Map.of("o", 9)));
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("e1");
    }

    @Test
    void emptyContextsShouldBeEqualButNotSame() {
        assertThat(new WorkflowContext()).isEqualTo(new WorkflowContext())
                .hasSameHashCodeAs(new WorkflowContext())
                .isNotSameAs(new WorkflowContext());
    }

    @Test
    void toStringShouldContainAllPropertyNames() {
        String text = context("exec-9", Map.of(), null, null).toString();

        assertThat(text).contains("executionId=exec-9").contains("variables=").contains("inputs=")
                .contains("outputs=");
    }

    @Test
    void privateFieldsAreReachableThroughReflectionUtils() {
        WorkflowContext context = new WorkflowContext();
        ReflectionTestUtils.setField(context, "executionId", "via-reflection");
        ReflectionTestUtils.setField(context, "variables", Map.of("x", "y"));

        assertThat(context.getExecutionId()).isEqualTo("via-reflection");
        assertThat(context.getVariables()).containsEntry("x", "y");
        assertThat(ReflectionTestUtils.getField(context, "executionId")).isEqualTo("via-reflection");
    }

    private WorkflowContext context(String executionId, Map<String, Object> variables,
                                    Map<String, Object> inputs, Map<String, Object> outputs) {
        WorkflowContext context = new WorkflowContext();
        context.setExecutionId(executionId);
        context.setVariables(variables);
        context.setInputs(inputs);
        context.setOutputs(outputs);
        return context;
    }
}
