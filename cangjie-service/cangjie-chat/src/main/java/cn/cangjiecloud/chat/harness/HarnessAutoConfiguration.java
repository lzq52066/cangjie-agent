package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.core.harness.ModelGateway;
import cn.cangjiecloud.core.harness.ResumeResolver;
import cn.cangjiecloud.core.harness.ToolGateway;
import cn.cangjiecloud.core.harness.impl.DefaultAgentHarness;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Harness 装配。
 * <p>
 * 引擎本身是纯对象，依赖的四个 SPI 全部由 Spring 管理：模型网关与工具网关来自本模块，
 * 留痕与审批存储复用 observability 已有的 service（它们已实现对应接口）。
 * {@code cangjie.harness.enabled=false} 时不装配引擎，对话完全走既有实现，可随时回滚。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "cangjie.harness", name = "enabled", havingValue = "true")
public class HarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentHarness.class)
    public AgentHarness agentHarness(ModelGateway modelGateway,
                                     ToolGateway toolGateway,
                                     AgentRunRecorder recorder,
                                     ObjectProvider<ApprovalStore> approvalStore,
                                     ObjectProvider<ResumeResolver> resumeResolver) {
        ApprovalStore store = approvalStore.getIfAvailable();
        ResumeResolver resolver = resumeResolver.getIfAvailable();
        if (store == null) {
            log.warn("未提供 ApprovalStore，需要人工审批的工具调用将被拒绝而不是挂起");
        }
        if (resolver == null) {
            log.warn("未提供 ResumeResolver，挂起的 run 无法恢复");
        }
        log.info("Agent Harness 已启用: 审批存储={}, 恢复解析器={}",
                store == null ? "无" : store.getClass().getSimpleName(),
                resolver == null ? "无" : resolver.getClass().getSimpleName());
        return new DefaultAgentHarness(modelGateway, toolGateway, recorder, store, resolver);
    }
}
