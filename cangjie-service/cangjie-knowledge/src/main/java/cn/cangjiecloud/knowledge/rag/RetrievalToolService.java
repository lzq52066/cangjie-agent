package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agentic RAG 检索工具服务
 * <p>
 * 将知识库检索封装为 Tool，供 LLM 在 Function Calling 中自主调用。
 * 当应用 ragMode=agentic 时，ChatServiceImpl 不再预处理检索结果注入 system prompt，
 * 而是将此 Tool 注册到函数调用循环中，由 LLM 自行决定何时检索、检索什么内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalToolService {

    public static final String TOOL_NAME = "search_knowledge_base";
    private static final int DEFAULT_TOP_K = 5;

    private final HybridRetriever hybridRetriever;

    /**
     * 构建检索工具的 ToolSpecification
     */
    public ToolSpecification buildSpec(String toolId) {
        return ToolSpecification.builder()
                .toolId(toolId)
                .name(TOOL_NAME)
                .description("搜索知识库获取相关内容。当你需要查找参考资料、核实事实或补充知识时使用。")
                .toolType("retrieval")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "检索查询词，包含需要查找的关键信息"
                                )
                        ),
                        "required", List.of("query")
                ))
                .build();
    }

    /**
     * 执行检索
     *
     * @param query 检索查询词
     * @param kbIds 知识库 ID 列表
     * @return 检索结果文本
     */
    public String execute(String query, List<String> kbIds) {
        if (query == null || query.isEmpty()) {
            return "检索失败：查询词不能为空";
        }
        if (kbIds == null || kbIds.isEmpty()) {
            return "检索失败：未指定知识库";
        }
        try {
            log.info("Agentic RAG 检索: query={}, kbIds={}", query, kbIds);
            List<RetrievalResult> results = hybridRetriever.retrieve(query, kbIds, DEFAULT_TOP_K);
            if (results.isEmpty()) {
                return "未检索到相关内容。";
            }
            StringBuilder sb = new StringBuilder("检索结果（共 " + results.size() + " 条）：\n\n");
            for (int i = 0; i < results.size(); i++) {
                RetrievalResult r = results.get(i);
                sb.append("[").append(i + 1).append("] ").append(r.getContent());
                sb.append("\n（来源: ").append(r.getDocumentId()).append(", 分数: ")
                        .append(String.format("%.2f", r.getFinalScore())).append("）\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Agentic RAG 检索失败: {}", e.getMessage(), e);
            return "检索失败: " + e.getMessage();
        }
    }
}