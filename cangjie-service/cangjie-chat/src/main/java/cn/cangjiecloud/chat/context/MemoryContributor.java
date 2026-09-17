package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.memory.MemoryScorer;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆（MEMORY 槽位，可压缩）。
 * <p>
 * 用户记忆按强度评分取 TopN、分维度共享字符预算，场景记忆续用同一预算；
 * 被注入的记忆会计一次触发（强化评分）。装配位置在检索之后、历史之前，
 * 与改造前"system 之后、历史之前"的相对顺序一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryContributor implements ContextContributor {

    private final ILongTermMemoryService longTermMemoryService;
    private final MemoryScorer memoryScorer;

    /** 记忆注入最大条数（按强度评分取 TopN） */
    @Value("${cangjie.memory.inject.max-count:20}")
    private int memoryInjectMaxCount;

    /** 记忆注入最大字符数 */
    @Value("${cangjie.memory.inject.max-chars:2000}")
    private int memoryInjectMaxChars;

    @Override
    public String name() {
        return "longTermMemory";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.MEMORY;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request.isMemoryEnabled()
                && StringUtils.hasText(request.getUserId())
                && StringUtils.hasText(request.getApplicationId());
    }

    @Override
    public ContextFragment contribute(ContextRequest request) {
        String userId = request.getUserId();
        String appId = request.getApplicationId();
        StringBuilder memoryPrompt = new StringBuilder();

        // 1. 用户记忆：按强度评分排序取 TopN（避免记忆膨胀稀释上下文）
        List<LongTermMemoryEntity> userMemories = longTermMemoryService.findActiveAll(userId, appId).stream()
                .filter(m -> !"scene".equals(m.getMemoryType()))
                .sorted(Comparator.comparingDouble(memoryScorer::score).reversed())
                .limit(memoryInjectMaxCount)
                .toList();

        List<String> dimensionOrder = Arrays.asList("preference", "background", "convention", "goal");
        Map<String, String> dimLabels = Map.of(
                "preference", "【用户偏好】",
                "background", "【用户背景】",
                "convention", "【用户习惯】",
                "goal", "【用户目标】");

        int charBudget = memoryInjectMaxChars;
        for (String dim : dimensionOrder) {
            List<LongTermMemoryEntity> memories = userMemories.stream()
                    .filter(m -> dim.equals(m.getDimension()))
                    .toList();
            if (memories.isEmpty()) {
                continue;
            }
            StringBuilder block = new StringBuilder();
            block.append(dimLabels.getOrDefault(dim, "【" + dim + "】")).append("\n");
            boolean any = false;
            for (LongTermMemoryEntity m : memories) {
                String line = "- " + m.getContent() + "\n";
                if (block.length() + line.length() > charBudget) {
                    break;
                }
                block.append(line);
                charBudget -= line.length();
                any = true;
                longTermMemoryService.incrementTrigger(m.getId());
            }
            if (any) {
                memoryPrompt.append(block).append("\n");
            }
        }

        // 2. 场景记忆：当前会话沉淀的事实（任务背景、约定等），与用户记忆共享同一字符预算
        List<LongTermMemoryEntity> sceneMemories = longTermMemoryService
                .findSceneMemories(request.getSessionId());
        if (!sceneMemories.isEmpty()) {
            StringBuilder sceneBlock = new StringBuilder("【当前会话背景】\n");
            for (LongTermMemoryEntity m : sceneMemories) {
                String line = "- " + m.getContent() + "\n";
                if (line.length() > charBudget) {
                    break;
                }
                sceneBlock.append(line);
                charBudget -= line.length();
                longTermMemoryService.incrementTrigger(m.getId());
            }
            memoryPrompt.append(sceneBlock);
        }

        if (memoryPrompt.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }
        log.debug("已注入记忆: userId={}, appId={}, scene={} 条, user={} 条",
                userId, appId, sceneMemories.size(), userMemories.size());
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(
                "以下是关于当前用户与当前会话的记忆信息，请在回答时参考：\n\n" + memoryPrompt.toString().trim())));
    }
}
