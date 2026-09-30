package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.core.harness.ModelGateway;
import cn.cangjiecloud.core.harness.ResumeResolver;
import cn.cangjiecloud.core.harness.ToolGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HarnessAutoConfiguration} 单元测试：验证 Harness Bean 在审批存储与
 * 恢复解析器存在/缺失两种情况下都能正常装配。
 */
class HarnessAutoConfigurationTest {

    /** 被测试自动装配类 */
    private final HarnessAutoConfiguration configuration = new HarnessAutoConfiguration();

    /**
     * 构造 ObjectProvider 桩
     */
    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    /** 两个可选 SPI 均存在时装配出非空 Harness */
    @Test
    void shouldCreateHarnessWithOptionalSpiPresent() {
        AgentHarness harness = configuration.agentHarness(
                mock(ModelGateway.class), mock(ToolGateway.class), mock(AgentRunRecorder.class),
                providerOf(mock(ApprovalStore.class)),
                providerOf(mock(ResumeResolver.class)));

        assertThat(harness).isNotNull();
    }

    /** 两个可选 SPI 均缺失时仍能完成装配（引擎内部按拒绝/不可恢复处理） */
    @Test
    void shouldCreateHarnessWhenOptionalSpiMissing() {
        AgentHarness harness = configuration.agentHarness(
                mock(ModelGateway.class), mock(ToolGateway.class), mock(AgentRunRecorder.class),
                providerOf(null), providerOf(null));

        assertThat(harness).isNotNull();
    }
}
