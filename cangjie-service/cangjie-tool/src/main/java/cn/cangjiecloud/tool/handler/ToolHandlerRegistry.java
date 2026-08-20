package cn.cangjiecloud.tool.handler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具处理器注册中心 — 按 toolType 存储 handler 实例
 */
@Component
public class ToolHandlerRegistry {

    private final Map<String, AbsToolHandler> handlers = new ConcurrentHashMap<>();

    public void register(String toolType, AbsToolHandler handler) {
        if (toolType != null && handler != null) {
            handlers.put(toolType, handler);
        }
    }

    public AbsToolHandler get(String toolType) {
        return handlers.get(toolType);
    }

    public boolean contains(String toolType) {
        return handlers.containsKey(toolType);
    }

    public Map<String, AbsToolHandler> getAll() {
        return Map.copyOf(handlers);
    }
}