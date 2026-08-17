package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环节点 — 遍历列表或按次数循环
 * <p>
 * config 参数：
 * - loopVariable: 遍历变量名（从 inputs 中取值，应为 List）
 * - maxIterations: 最大循环次数（默认 10）
 * <p>
 * 输出:
 * - loop_items: 遍历的列表
 * - loop_count: 实际循环次数
 * - loop_current: 当前项（最后一次迭代）
 */
@Slf4j
@Component
public class LoopNode implements WorkflowNode {

    @Override
    public String getType() {
        return "loop";
    }

    @Override
    public String getName() {
        return "循环";
    }

    @Override
    public String getDescription() {
        return "遍历列表或按次数循环执行";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        int maxIterations = safeConfig.containsKey("maxIterations")
                ? ((Number) safeConfig.get("maxIterations")).intValue()
                : 10;

        // 获取循环列表
        String loopVar = (String) safeConfig.get("loopVariable");
        List<Object> items = new ArrayList<>();

        if (loopVar != null && !loopVar.isEmpty()) {
            Object val = safeInputs.get(loopVar);
            if (val instanceof List) {
                items.addAll((List<?>) val);
            } else if (val != null) {
                items.add(val);
            }
        }

        // 限制最大次数
        int count = Math.min(items.size(), maxIterations);
        Object currentItem = null;
        List<Object> processedItems = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            currentItem = items.get(i);
            processedItems.add(currentItem);
        }

        log.info("LoopNode: 循环 {} 次 (最大 {})", count, maxIterations);

        Map<String, Object> output = new HashMap<>();
        output.put("loop_items", processedItems);
        output.put("loop_count", count);
        output.put("loop_current", currentItem);
        return output;
    }
}
