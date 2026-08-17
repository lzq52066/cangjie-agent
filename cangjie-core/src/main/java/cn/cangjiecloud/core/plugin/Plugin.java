package cn.cangjiecloud.core.plugin;

/**
 * 插件统一接口
 * <p>
 * 所有工具、函数、通道、解析器、处理器等均可实现此接口，
 * 由 {@link PluginRegistry} 自动收集并按 name 注册。
 */
public interface Plugin {

    /** 插件名称（唯一标识） */
    String getName();

    /** 插件描述 */
    String getDescription();

    /**
     * 插件类型：tool / function / channel / parser / processor
     */
    String getType();

    /**
     * 执行插件
     *
     * @param context 插件执行上下文
     * @return 执行结果
     */
    Object execute(PluginContext context);
}
