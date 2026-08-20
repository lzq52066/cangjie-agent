package cn.cangjiecloud.tool.executor;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Groovy 脚本执行器 — 三层安全沙箱
 */
@Slf4j
public class GroovyScriptExecutor {

    private static final long TIMEOUT_SECONDS = 30;

    /** 脚本执行结果包装 */
    public record ScriptResult(Object result, String error, long durationMs) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    public ScriptResult execute(String scriptCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Object> future = executor.submit(() -> {
            // 安全沙箱配置
            SecureASTCustomizer secure = new SecureASTCustomizer();
            secure.setClosuresAllowed(true);
            secure.setMethodDefinitionAllowed(false);
            secure.setImportsWhitelist(Collections.singletonList(
                    "java.util.Map"
            ));
            secure.setStarImportsWhitelist(Collections.singletonList(
                    "java.lang"
            ));

            ImportCustomizer imports = new ImportCustomizer();
            imports.addStarImports("java.util", "java.lang", "java.math");

            CompilerConfiguration config = new CompilerConfiguration();
            config.addCompilationCustomizers(secure, imports);

            Binding binding = new Binding();
            if (params != null) {
                params.forEach(binding::setVariable);
            }

            GroovyShell shell = new GroovyShell(binding, config);
            return shell.evaluate(scriptCode);
        });

        try {
            Object result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long cost = System.currentTimeMillis() - start;
            log.info("Groovy 脚本执行成功, 耗时 {}ms", cost);
            return new ScriptResult(result, null, cost);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Groovy 脚本执行超时");
            return new ScriptResult(null, "脚本执行超时（超过 " + TIMEOUT_SECONDS + " 秒）", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Groovy 脚本执行异常", e);
            return new ScriptResult(null, e.getMessage(), System.currentTimeMillis() - start);
        } finally {
            executor.shutdownNow();
        }
    }
}