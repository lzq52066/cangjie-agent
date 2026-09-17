package cn.cangjiecloud.core.harness.context;

import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.ModelSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 上下文装配请求（Contributor 的只读输入）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextRequest {

    private String applicationId;

    private String sessionId;

    private String userId;

    private String modelId;

    private String modelName;

    /** 本轮用户输入 */
    private String userQuery;

    /** 知识库 ID（RAG / agentic 检索使用） */
    private List<String> knowledgeBaseIds;

    /** 记忆是否开启 */
    private boolean memoryEnabled;

    /** 最大历史轮数 */
    private Integer maxTurns;

    /** RAG 模式：generic / agentic / disabled */
    private String ragMode;

    private ModelSettings modelSettings;

    private HarnessConfig config;

    /** 透传扩展（如提示词模板 ID、场景 ID），避免 Contributor 反向依赖业务实体 */
    private Map<String, Object> attributes;
}
