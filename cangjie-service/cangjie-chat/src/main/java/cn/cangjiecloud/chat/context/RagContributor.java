package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.observability.TraceSpan;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalOptions;
import cn.cangjiecloud.core.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库检索（RAG 槽位，可压缩）。
 * <p>
 * 改造前检索文本内嵌在系统消息里；独立成槽后既能在留痕中看到检索占用，也让预算裁剪
 * 可以整段丢弃知识库内容而不影响人设。查询改写（默认关闭）在此完成，历史经
 * {@link ContextAttrs#HISTORY_MESSAGES} 复用不再二次查库；agentic 模式跳过预处理检索，
 * 由 LLM 自主调用检索工具。
 * <p>
 * 检索 span 除耗时外还落本次输入（原 query / 改写后 query / 检索口径）与输出（召回片段的
 * 各通道分数与名次）：「为什么没查到」只能靠这些证据回答，光有一句「检索 5 条结果」等于没留痕。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagContributor implements ContextContributor {

    /** 片段正文写入留痕时的截断长度：够定位「召回的是哪一段」，又不至于把 trace 表撑爆 */
    private static final int MAX_SNIPPET_CHARS = 500;

    /**
     * 送进模型的单段正文上限。
     * <p>
     * 与留痕用的 {@link #MAX_SNIPPET_CHARS} 分开：留痕要能认出是哪一段，送模型则要控住上下文占用。
     */
    @Value("${cangjie.rag.max-snippet-chars:2000}")
    private int maxSnippetChars = 2000;

    /** 全部片段合计送进模型的上限（约 6k token），超出的片段直接不进 prompt */
    @Value("${cangjie.rag.max-prompt-chars:12000}")
    private int maxPromptBudgetChars = 12000;

    private final HybridRetriever hybridRetriever;
    private final QueryRewriterFactory queryRewriterFactory;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    /** 检索条数（可配置：cangjie.rag.top-k）；此前写死 5，改配置要动代码 */
    @Value("${cangjie.rag.top-k:5}")
    private int topK = 5;

    /** 查询改写开关（默认关闭，关闭时行为与改造前完全一致） */
    @Value("${cangjie.rag.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    /** 查询改写器类型（none / llm） */
    @Value("${cangjie.rag.query-rewrite.type:none}")
    private String queryRewriteType;

    /**
     * 是否把用户 query 与知识库正文写进检索载荷。
     * <p>
     * 模型载荷已经有 {@code llm-trace.store-content} 这道闸门，载荷留痕不能再背后开一条绕过它的
     * 写入路径，否则「关掉正文存储」这个开关说了不算。默认与那道闸门对齐。
     * 关闭后仍然落下长度与指纹：「用的是哪一句 query、改写过没」这类诊断信息不能一起丢。
     */
    @Value("${cangjie.observability.trace-payload.enabled:${cangjie.observability.llm-trace.store-content:true}}")
    private boolean tracePayloadEnabled;

    @Override
    public String name() {
        return "rag";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.RAG;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(ContextRequest request) {
        return request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()
                && !"agentic".equals(request.getRagMode());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        String originalQuery = request.getUserQuery();
        String query = originalQuery;
        if (queryRewriteEnabled) {
            Object history = request.getAttributes().get(ContextAttrs.HISTORY_MESSAGES);
            query = queryRewriterFactory.get(queryRewriteType)
                    .rewrite(query, history instanceof List<?> list ? (List<ChatMessage>) list : List.of());
        }

        long retrievalStart = System.currentTimeMillis();
        // 只给知识库与 topK，其余口径（模式/融合/重排）由检索侧按知识库配置解析，
        // 并把实际生效的口径回写进 span，否则「线上这次到底怎么检的」在链路里看不到
        RetrievalOptions options = RetrievalOptions.builder()
                .knowledgeBaseIds(request.getKnowledgeBaseIds())
                .topK(topK)
                .build();
        List<RetrievalResult> results;
        try {
            results = hybridRetriever.retrieve(query, options);
        } catch (Exception e) {
            // 失败时同样把输入落库：不知道用了什么 query 的失败检索无法复现
            recordTrace(request, originalQuery, query, options, retrievalStart, "fail",
                    "知识库检索失败: " + e.getMessage(), List.of(), List.of());
            log.warn("知识库检索失败: {}", e.getMessage());
            return ContextFragment.empty(slot(), name());
        }
        // 降级要留在链路里：向量路是唯一受相似度阈值约束的通道，它挂了这批结果就是无阈值全文召回，
        // 而 span 若仍是一句「检索 5 条结果 / success」，排障方向会被彻底带偏（看着像召回质量差）。
        // 状态沿用 success —— 检索确实返回了东西，标成 fail 会和「整条请求失败」混在一起；
        // 区分靠 message 与 metadata.failedChannels
        List<String> failedChannels = failedChannelsOf(results);
        String outcome = "知识库检索: " + results.size() + " 条结果"
                + (failedChannels.isEmpty() ? ""
                : "（降级：仅 " + survivingChannels(failedChannels) + " 通道，无相似度阈值过滤）");
        recordTrace(request, originalQuery, query, options, retrievalStart, "success", outcome, results, failedChannels);
        if (!failedChannels.isEmpty()) {
            log.warn("知识库检索降级: 通道 {} 失败，本次结果未经相似度阈值过滤", failedChannels);
        }
        if (results.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }

        StringBuilder prompt = new StringBuilder("以下是从知识库中检索到的相关内容：\n\n");
        Object sources = request.getAttributes().get(ContextAttrs.RETRIEVAL_SOURCES);
        List<Map<String, Object>> retrievalSources =
                sources instanceof List<?> list ? (List<Map<String, Object>>) list : null;
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            // 进 prompt 的正文要设上限：条数只由 cangjie.rag.top-k 控制，单个 chunk 配大时
            // 几段原文就能把上下文窗口占满，把系统提示与历史挤出去（MAX_SNIPPET_CHARS 管的是留痕，不是出网）
            if (prompt.length() >= maxPromptBudgetChars) {
                log.warn("知识库片段总量已达 prompt 上限（{} 字），本次只送前 {} 段",
                        maxPromptBudgetChars, i);
                break;
            }
            String body = promptSnippet(r.getContent());
            prompt.append("【片段").append(i + 1).append("】")
                    .append("来源：").append(getDocumentName(r)).append("\n")
                    .append("内容：").append(body).append("\n\n");

            if (retrievalSources != null) {
                Map<String, Object> source = new HashMap<>();
                source.put("paragraphId", r.getParagraphId());
                source.put("documentId", r.getDocumentId());
                source.put("knowledgeBaseId", r.getKnowledgeBaseId());
                source.put("content", r.getContent());
                source.put("score", r.getFinalScore());
                source.put("documentName", getDocumentName(r));
                retrievalSources.add(source);
            }
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(prompt.toString().trim())));
    }

    /**
     * 把一次检索写为带载荷的 span。
     * <p>
     * 留痕失败绝不能影响对话：载荷序列化异常在此兜住，只记日志。
     */
    private void recordTrace(ContextRequest request, String originalQuery, String effectiveQuery,
                            RetrievalOptions options, long startMs, String status, String message,
                            List<RetrievalResult> results, List<String> failedChannels) {
        if (traceCollector == null) {
            return;
        }
        long duration = System.currentTimeMillis() - startMs;
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            if (tracePayloadEnabled) {
                input.put("query", originalQuery);
                input.put("effectiveQuery", effectiveQuery);
            } else {
                // 不落正文也要能判断「是不是同一句 query / 改写有效没」：留长度与指纹
                input.put("queryLength", lengthOf(originalQuery));
                input.put("queryFingerprint", fingerprint(originalQuery));
                input.put("effectiveQueryLength", lengthOf(effectiveQuery));
                input.put("effectiveQueryFingerprint", fingerprint(effectiveQuery));
            }
            input.put("queryRewritten", !Objects.equals(originalQuery, effectiveQuery));
            input.put("knowledgeBaseIds", options.getKnowledgeBaseIds());
            input.put("topK", options.getTopK());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("candidateCount", results.size());
            if (!failedChannels.isEmpty()) {
                metadata.put("failedChannels", failedChannels);
            }

            TraceSpan span = TraceSpan.builder()
                    .module("retrieval").action("search").traceId(request.getTraceId())
                    .duration(duration).status(status).message(message)
                    .input(JsonUtils.toJSONString(input))
                    .output(results.isEmpty() ? null : JsonUtils.toJSONString(toPayload(results)))
                    .metadata(metadata)
                    .build();
            traceCollector.record(span);
        } catch (Exception e) {
            log.warn("检索 span 载荷写入失败，仅记耗时: {}", e.getMessage());
            traceCollector.record(TraceSpan.of("retrieval", "search", request.getTraceId(),
                    duration, status, message));
        }
    }

    /**
     * 片段留痕只留诊断必需字段，正文开关控制下才落、落也按需截断。
     * <p>
     * 关闭正文时 paragraphId / 分数 / 名次照旧入库：足够回答「召回了哪一段、排第几」，
     * 只是看不到这一段写了什么。
     */
    private List<Map<String, Object>> toPayload(List<RetrievalResult> results) {
        List<Map<String, Object>> candidates = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("paragraphId", r.getParagraphId());
            item.put("documentId", r.getDocumentId());
            item.put("documentName", getDocumentName(r));
            item.put("score", r.getFinalScore());
            item.put("vectorScore", r.getVectorScore());
            item.put("fullTextScore", r.getFullTextScore());
            if (tracePayloadEnabled) {
                item.put("content", truncate(r.getContent()));
            }
            candidates.add(item);
        }
        return candidates;
    }

    private static Integer lengthOf(String text) {
        return text == null ? null : text.length();
    }

    /** 取 SHA-256 前 8 字节的十六进制：能比对同一句 query，又不把原文留下来 */
    private static String fingerprint(String text) {
        if (text == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // 没 SHA-256 的环境退化到 hashCode，只是一个相关性提示，不影响主流程
            return Integer.toHexString(text.hashCode());
        }
    }

    private String truncate(String content) {
        if (content == null || content.length() <= MAX_SNIPPET_CHARS) {
            return content;
        }
        return content.substring(0, MAX_SNIPPET_CHARS) + "...[truncated]";
    }

    /** 送进模型的正文裁剪，与留痕的 {@link #truncate(String)} 各用各的上限 */
    private String promptSnippet(String content) {
        if (content == null || content.length() <= maxSnippetChars) {
            return content;
        }
        return content.substring(0, maxSnippetChars) + "...[truncated]";
    }

    /**
     * 从结果里读回「哪一路挂了」（由检索侧写在 {@link RetrievalResult#META_FAILED_CHANNELS} 里）。
     */
    private static List<String> failedChannelsOf(List<RetrievalResult> results) {
        for (RetrievalResult r : results) {
            if (r.getMetadata() == null) {
                continue;
            }
            Object failed = r.getMetadata().get(RetrievalResult.META_FAILED_CHANNELS);
            if (failed instanceof List<?> list && !list.isEmpty()) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    /** 降级提示要说的是「还剩哪一路」，不是「挂了哪一路」 */
    private static String survivingChannels(List<String> failed) {
        List<String> all = new ArrayList<>(List.of("向量", "全文"));
        if (failed.contains("vector")) {
            all.remove("向量");
        }
        if (failed.contains("fullText")) {
            all.remove("全文");
        }
        return all.isEmpty() ? "无可用通道" : String.join("+", all);
    }

    private String getDocumentName(RetrievalResult r) {
        if (r.getMetadata() != null) {
            Object name = r.getMetadata().get("title");
            if (name != null) {
                return name.toString();
            }
        }
        return r.getDocumentId() != null ? r.getDocumentId() : "未知文档";
    }
}
