package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.knowledge.rag.RetrievalToolService;
import cn.cangjiecloud.tool.service.IToolService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具规格装配器：把散落在同步/流式两条路径中的三处工具装配收敛为一处。
 * <p>
 * 装配顺序与改造前完全一致（应用工具 → 技能 → agentic 检索工具），
 * 顺序会影响模型可选工具列表的排列，进而影响模型行为，因此不可调整。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolSpecAssembler {

    public static final String AGENTIC_RETRIEVAL_SPEC_ID = "agentic-retrieval";

    private final IToolService toolService;
    private final RetrievalToolService retrievalToolService;

    /**
     * 按应用配置装配可用工具
     */
    public List<ToolSpecification> assemble(ApplicationEntity application) {
        List<ToolSpecification> specs = new ArrayList<>();
        if (application == null) {
            return specs;
        }
        List<String> toolIds = parseStringList(application.getToolIds());
        if (!toolIds.isEmpty()) {
            specs.addAll(toolService.getToolSpecifications(toolIds));
        }
        List<String> skillIds = parseStringList(application.getSkillIds());
        if (!skillIds.isEmpty()) {
            specs.addAll(toolService.getSkillSpecifications(skillIds));
        }
        List<String> kbIds = knowledgeBaseIds(application);
        if ("agentic".equals(application.getRagMode()) && !kbIds.isEmpty()) {
            specs.add(retrievalToolService.buildSpec(AGENTIC_RETRIEVAL_SPEC_ID));
        }
        return specs;
    }

    /**
     * 应用关联的知识库 ID
     */
    public List<String> knowledgeBaseIds(ApplicationEntity application) {
        return application == null ? List.of() : parseStringList(application.getKnowledgeBaseIds());
    }

    /**
     * 转换为 OpenAI function calling 的工具定义（与改造前的结构与顺序一致）
     */
    public static List<Map<String, Object>> toDefinitions(List<ToolSpecification> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        return specs.stream().map(spec -> {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("type", "function");
            Map<String, Object> function = new HashMap<>();
            function.put("name", spec.getName());
            function.put("description", spec.getDescription());
            if (spec.getParameters() != null) {
                function.put("parameters", spec.getParameters());
            } else {
                function.put("parameters", Map.of("type", "object", "properties", Map.of()));
            }
            toolDef.put("function", function);
            return toolDef;
        }).toList();
    }

    /**
     * 解析 JSON 数组字符串（与 {@code ChatServiceImpl#parseStringList} 语义一致）
     */
    public static List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            return array.stream()
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}, json={}", e.getMessage(), json);
            return new ArrayList<>();
        }
    }
}
