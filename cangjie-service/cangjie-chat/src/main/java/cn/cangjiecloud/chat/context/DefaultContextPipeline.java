package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.context.ContextBudget;
import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextPipeline;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextResult;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文装配管线默认实现。
 * <p>
 * 调度序（{@code getOrder()}）与装配序（槽位）解耦：贡献者按调度序执行（如历史必须先于
 * 检索产出，供查询改写复用），片段最终按 {@link ContextSlot} 顺序拼装。SYSTEM 槽位内的
 * 多个片段（模板/技能/规则/描述兜底）合并为单条 system 消息，与改造前的系统消息结构一致。
 * <p>
 * 单个贡献者抛异常只丢弃该片段并告警，不阻断装配——与原 {@code buildContext} 内
 * 逐段 try-catch 的容错语义一致。预算未启用（默认）时不裁剪，行为与现网等价。
 */
@Slf4j
@Component
public class DefaultContextPipeline implements ContextPipeline {

    private final List<ContextContributor> contributors;
    private final HarnessConfigResolver configResolver;

    public DefaultContextPipeline(List<ContextContributor> contributors, HarnessConfigResolver configResolver) {
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(ContextContributor::getOrder))
                .toList();
        this.configResolver = configResolver;
    }

    @Override
    public ContextResult assemble(ContextRequest request) {
        if (request.getAttributes() == null) {
            request.setAttributes(new HashMap<>());
        }
        List<ContextFragment> fragments = new ArrayList<>();
        // 已产出片段经 attributes 共享，供贡献者之间协作（如描述兜底判断模板/检索是否为空）
        request.getAttributes().put(ContextAttrs.FRAGMENTS, fragments);

        for (ContextContributor contributor : contributors) {
            if (!contributor.supports(request)) {
                continue;
            }
            ContextFragment fragment;
            try {
                fragment = contributor.contribute(request);
            } catch (Exception e) {
                log.warn("上下文贡献者执行失败，跳过该片段: contributor={}, {}", contributor.name(), e.getMessage());
                continue;
            }
            if (fragment == null || fragment.isEmpty()) {
                continue;
            }
            fragment.refreshEstTokens();
            fragments.add(fragment);
        }

        ContextBudget budget = configResolver.budget(request.getConfig());
        budget.apply(fragments);

        // 装配：按槽位顺序（同槽位保持贡献者调度序，稳定排序）
        List<ContextFragment> ordered = new ArrayList<>(fragments);
        ordered.sort(Comparator.comparingInt(f -> f.getSlot().order()));

        List<ChatMessage> messages = new ArrayList<>();
        StringBuilder systemBlock = new StringBuilder();
        int estTokens = 0;
        boolean trimmed = false;
        for (ContextFragment fragment : ordered) {
            estTokens += fragment.getEstTokens();
            trimmed = trimmed || fragment.getDroppedTokens() > 0;
            for (ChatMessage message : fragment.getMessages()) {
                if (fragment.getSlot() == ContextSlot.SYSTEM && "system".equals(message.getRole())) {
                    if (systemBlock.length() > 0) {
                        systemBlock.append("\n\n");
                    }
                    systemBlock.append(message.getContent());
                } else {
                    flushSystemBlock(messages, systemBlock);
                    messages.add(message);
                }
            }
        }
        flushSystemBlock(messages, systemBlock);

        return ContextResult.builder()
                .messages(messages)
                .fragments(fragments)
                .estTokens(estTokens)
                .trimmed(trimmed)
                .build();
    }

    private void flushSystemBlock(List<ChatMessage> messages, StringBuilder systemBlock) {
        if (systemBlock.length() == 0) {
            return;
        }
        messages.add(ChatMessage.system(systemBlock.toString().trim()));
        systemBlock.setLength(0);
    }
}
