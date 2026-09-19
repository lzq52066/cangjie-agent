package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.SkillToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.mapper.ToolMapper;
import cn.cangjiecloud.tool.util.ToolNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl extends ServiceImpl<ToolMapper, ToolEntity>
        implements IToolService {

    private final ApplicationContext applicationContext;
    private final ToolHandlerRegistry handlerRegistry;

    @Autowired(required = false)
    private SkillToolHandler skillToolHandler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolEntity create(ToolEntity entity) {
        validateFunctionName(entity, null);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        save(entity);
        log.info("工具已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolEntity update(String id, ToolEntity entity) {
        ToolEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("工具不存在");
        }
        validateFunctionName(entity, id);
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ToolEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        removeById(id);
        log.info("工具已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public IPage<ToolEntity> pageQuery(String keyword, String type, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ToolEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ToolEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ToolEntity::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(ToolEntity::getType, type);
        }
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    // ========== 工具执行（策略分发） ==========

    @Override
    public ToolExecuteResultDTO executeTool(String toolId, Map<String, Object> input) {
        ToolEntity entity = getActiveEntity(toolId);
        String resolvedType = resolveToolType(entity);
        if (ToolConstants.ToolType.LOCAL.equals(resolvedType)) {
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error("本地工具不在服务端执行：它由网页在用户浏览器授权目录内执行，请在对话中触发")
                    .build();
        }

        AbsToolHandler handler = handlerRegistry.get(resolvedType);
        if (handler == null) {
            // 兜底：使用旧版 Plugin 反射执行
            return executeLegacyPlugin(entity, input);
        }
        return handler.execute(entity, input);
    }

    /**
     * 旧版 Plugin 反射执行（兜底逻辑）
     */
    private ToolExecuteResultDTO executeLegacyPlugin(ToolEntity entity, Map<String, Object> input) {
        long start = System.currentTimeMillis();
        try {
            Plugin plugin = loadPlugin(entity.getImplementation());
            PluginContext context = PluginContext.builder()
                    .params(input)
                    .metadata(Map.of(
                            "toolId", entity.getId(),
                            "toolName", entity.getName() != null ? entity.getName() : ""
                    ))
                    .build();
            Object result = plugin.execute(context);
            long cost = System.currentTimeMillis() - start;
            log.info("工具执行成功(Plugin): {} ({}), 耗时 {}ms", entity.getName(), entity.getId(), cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

    // ========== ToolSpecification（Function Calling 用） ==========

    @Override
    public List<ToolSpecification> getToolSpecifications(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolEntity> tools = listByIds(toolIds);
        if (tools.isEmpty()) {
            return Collections.emptyList();
        }

        // 只处理激活的工具，按类型分组
        Map<String, List<ToolEntity>> grouped = tools.stream()
                .filter(t -> ToolConstants.STATUS_ACTIVE.equals(t.getStatus()))
                .collect(Collectors.groupingBy(this::resolveToolType));

        List<ToolSpecification> result = new ArrayList<>();
        for (Map.Entry<String, List<ToolEntity>> entry : grouped.entrySet()) {
            AbsToolHandler handler = handlerRegistry.get(entry.getKey());
            if (handler != null) {
                result.addAll(handler.buildToolSpecifications(entry.getValue()));
            } else {
                // 没有对应 handler 时使用基础构建
                result.addAll(entry.getValue().stream()
                        .map(this::buildBasicSpec)
                        .toList());
            }
        }
        return result;
    }

    private ToolSpecification buildBasicSpec(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(ToolNaming.resolveCallName(entity.getFunctionName(), entity.getId()))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(entity.getType())
                .parameters(parseJsonMap(entity.getParameters()))
                .build();
    }

    @Override
    public ToolEntity resolveByCallName(String callName) {
        String name = ToolNaming.normalizeFunctionName(callName);
        if (name == null) {
            return null;
        }
        // 1. 自定义函数名（与构建 ToolSpecification 时的命名规则一致）
        if (!ToolNaming.isReservedFunctionName(name)) {
            ToolEntity entity = getOne(new LambdaQueryWrapper<ToolEntity>()
                    .eq(ToolEntity::getFunctionName, name), false);
            if (entity != null) {
                return entity;
            }
        }
        // 2. 兼容历史 tool_<id> / skill_<id> 等带前缀的命名
        return getById(ToolNaming.parse(name));
    }

    @Override
    public String executeToolCall(String toolName, Map<String, Object> arguments) {
        // === 技能路由：skill_ 前缀直接走 SkillToolHandler ===
        if (toolName != null && toolName.startsWith(ToolNaming.SKILL_PREFIX)) {
            return executeSkillCall(toolName, arguments);
        }

        ToolEntity entity = resolveByCallName(toolName);
        if (entity == null) {
            log.warn("工具不存在: toolName={}", toolName);
            return JsonUtils.toJSONString(Map.of("success", false, "error", "工具不存在"));
        }

        String resolvedType = resolveToolType(entity);
        if (ToolConstants.ToolType.LOCAL.equals(resolvedType)) {
            return JsonUtils.toJSONString(Map.of("success", false,
                    "error", "本地工具不在服务端执行，需由网页在用户浏览器授权目录内执行"));
        }
        AbsToolHandler handler = handlerRegistry.get(resolvedType);
        if (handler == null) {
            log.warn("不支持的工具类型: toolName={}, toolType={}", toolName, resolvedType);
            return JsonUtils.toJSONString(Map.of("success", false, "error", "不支持的工具类型"));
        }

        var result = handler.execute(entity, arguments);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result.getOutput() != null ? result.getOutput().toString() : "";
        }
        return JsonUtils.toJSONString(Map.of("success", false, "error", result.getError()));
    }

    // ========== 被 ToolProviderServiceImpl 调用 ==========

    /**
     * 获取激活的技能规格列表（用于 LLM function calling）
     *
     * @param skillIds 技能 ID 列表
     * @return ToolSpecification 列表
     */
    public List<ToolSpecification> getSkillSpecifications(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty() || skillToolHandler == null) {
            return Collections.emptyList();
        }
        return skillIds.stream()
                .map(skillToolHandler::buildToolSpecification)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 执行技能调用（由 LLM function calling 触发，skill_ 前缀）
     */
    private String executeSkillCall(String toolName, Map<String, Object> arguments) {
        if (skillToolHandler == null) {
            return JsonUtils.toJSONString(Map.of("success", false, "error", "技能处理器未初始化"));
        }
        String skillId = ToolNaming.parse(toolName);
        var result = skillToolHandler.execute(skillId, arguments);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result.getOutput() != null ? result.getOutput().toString() : "";
        }
        return JsonUtils.toJSONString(Map.of("success", false, "error", result.getError()));
    }

    // ========== 内部工具方法 ==========

    /**
     * 归一化并校验函数名：格式合法、不使用平台保留名、在所有工具中唯一。
     * 允许为空（此时 function calling 回退为 tool_&lt;id&gt;）。
     */
    private void validateFunctionName(ToolEntity entity, String selfId) {
        String name = ToolNaming.normalizeFunctionName(entity.getFunctionName());
        entity.setFunctionName(name);
        if (name == null) {
            return;
        }
        if (!ToolNaming.isValidFunctionName(name)) {
            throw new ApiException("函数名只允许字母、数字、下划线和中划线，且必须以字母开头（长度 1-64）: " + name);
        }
        if (ToolNaming.isReservedFunctionName(name)) {
            throw new ApiException("函数名 " + name + " 与平台内置命名规则冲突，请换一个");
        }
        boolean duplicated = count(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getFunctionName, name)
                .ne(selfId != null, ToolEntity::getId, selfId)) > 0;
        if (duplicated) {
            throw new ApiException("函数名已存在: " + name);
        }
    }

    private ToolEntity getActiveEntity(String toolId) {
        ToolEntity entity = getById(toolId);
        if (entity == null) {
            throw new ApiException("工具不存在");
        }
        if (!ToolConstants.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ApiException("工具未激活");
        }
        return entity;
    }

    private String resolveToolType(ToolEntity entity) {
        // 优先使用新 toolType 字段
        if (StringUtils.hasText(entity.getToolType())) {
            return entity.getToolType();
        }
        // 兼容旧 type 字段
        if (StringUtils.hasText(entity.getType())) {
            return switch (entity.getType()) {
                case "HTTP", "http" -> ToolConstants.ToolType.HTTP;
                case "CUSTOM", "custom" -> ToolConstants.ToolType.CUSTOM;
                case "MCP", "mcp" -> ToolConstants.ToolType.MCP;
                case "SKILL", "skill" -> ToolConstants.ToolType.SKILL;
                default -> ToolConstants.ToolType.PLUGIN;
            };
        }
        return ToolConstants.ToolType.PLUGIN;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return JsonUtils.parseMap(json);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 通过 implementation 全限定类名加载 Plugin：
     * 1. 优先从 Spring 容器获取 bean；
     * 2. 容器中不存在时反射实例化。
     */
    private Plugin loadPlugin(String className) {
        if (!StringUtils.hasText(className)) {
            throw new ApiException("工具未配置实现类");
        }
        try {
            Class<?> clazz = Class.forName(className);
            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new ApiException("实现类未实现 Plugin 接口: " + className);
            }
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) clazz;
            try {
                return applicationContext.getBean(pluginClass);
            } catch (NoSuchBeanDefinitionException e) {
                return pluginClass.getDeclaredConstructor().newInstance();
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("加载插件失败: " + className + ", " + e.getMessage());
        }
    }
}
