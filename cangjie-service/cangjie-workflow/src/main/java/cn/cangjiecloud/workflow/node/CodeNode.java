package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.tool.executor.GroovyScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码节点 — 执行 Groovy 脚本
 * <p>
 * config 参数：
 * - script: Groovy 脚本代码，可通过 input.xxx 访问上下文变量
 * <p>
 * 输出:
 * - code_output: 脚本返回值
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
        return "执行自定义 Groovy 脚本";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        String script = (String) safeConfig.get("script");

        if (script == null || script.isEmpty()) {
            return Map.of("code_output", "");
        }

        // 将输入绑定到脚本：既暴露 input 整体，也逐个暴露变量名
        Map<String, Object> params = new HashMap<>();
        params.put("input", safeInputs);
        params.putAll(safeInputs);

        GroovyScriptExecutor.ScriptResult result =
                new GroovyScriptExecutor().execute(script, params);

        log.info("CodeNode: Groovy 脚本执行完成");

        Map<String, Object> output = new HashMap<>();
        if (result.isSuccess()) {
            output.put("code_output", result.result() != null ? result.result() : "");
        } else {
            log.error("CodeNode: Groovy 脚本执行失败: {}", result.error());
            output.put("code_output", "");
            output.put("code_error", result.error());
        }
        return output;
    }
}
