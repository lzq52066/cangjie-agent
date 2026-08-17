package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码节点 — 执行脚本代码
 * <p>
 * config 参数：
 * - language: 脚本语言（默认 javascript/nashorn）
 * - script: 脚本代码，可通过 input.xxx 访问上下文变量
 * <p>
 * 输出:
 * - code_output: 脚本返回值或 result 变量
 */
@Slf4j
@Component
public class CodeNode implements WorkflowNode {

    @Override
    public String getType() {
        return "code";
    }

    @Override
    public String getName() {
        return "代码";
    }

    @Override
    public String getDescription() {
        return "执行自定义脚本代码";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        String language = (String) safeConfig.getOrDefault("language", "javascript");
        String script = (String) safeConfig.get("script");

        if (script == null || script.isEmpty()) {
            return Map.of("code_output", "");
        }

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName(language);
            if (engine == null) {
                // 尝试 js/nashorn 别名
                engine = manager.getEngineByName("js");
            }
            if (engine == null) {
                return Map.of("code_output", "", "code_error", "不支持的脚本语言: " + language);
            }

            // 将输入变量绑定到脚本上下文
            SimpleBindings bindings = new SimpleBindings();
            bindings.put("input", safeInputs);
            // 同时将每个变量直接暴露
            safeInputs.forEach(bindings::put);

            Object result = engine.eval(script, bindings);

            log.info("CodeNode: 脚本执行完成");

            Map<String, Object> output = new HashMap<>();
            output.put("code_output", result != null ? result.toString() : "");
            return output;

        } catch (Exception e) {
            log.error("CodeNode: 脚本执行失败", e);
            return Map.of("code_output", "", "code_error", e.getMessage());
        }
    }
}
