package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.eval.ContextChunk;
import cn.cangjiecloud.core.eval.OnlineTraceSample;
import cn.cangjiecloud.core.eval.OnlineTraceSource;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.LlmTraceMapper;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线评测的样本来源：把「一次线上调用」从 {@code llm_trace} + 链路 span 还原成可打分的样本。
 * <p>
 * 为什么不在评测模块里直接读这两张表：可观测性的表结构属于观测模块，把它拖进评测侧
 * 等于让两边一起跟着列名变。跨模块只认 core 的 {@link OnlineTraceSource}。
 * <p>
 * 真实调用没有标注，因此样本只带三样东西：问题、回答、当次召回的片段。问题与片段都优先取
 * 检索 span 的载荷 —— 那才是当时真正 used 的口径；载荷开关关掉时正文不落库，
 * 样本照样发出去，由指标自己按「缺片段就不出片段类的分」处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineTraceSourceImpl implements OnlineTraceSource {

    /**
     * 单次最多捞多少条：一轮在线评测要烧判定 token，一次捞太多等于把线上流量拿去做批量评估，
     * 出问题时钱已经花出去了。要评更多就分几轮、每轮都能中途停。
     */
    private static final int MAX_FETCH_LIMIT = 200;

    private static final String STATUS_SUCCESS = "success";
    private static final String MODULE_RETRIEVAL = "retrieval";
    private static final String ACTION_SEARCH = "search";

    /**
     * 评测自己发起的调用的 traceId 前缀（离线跑与在线判定都走这个前缀）。
     * <p>
     * 不能不排：判定本身也是一次模型调用、也会落进这张表，不把评测自己产出的调用挡掉，
     * 下一轮就会拿判定调用的输出当「线上问答」再评一遍，样本越评越多、token 越烧越快。
     */
    private static final String EVAL_TRACE_PREFIX = "eval-";

    private final LlmTraceMapper llmTraceMapper;
    private final TraceRecordMapper traceRecordMapper;

    @Override
    public List<OnlineTraceSample> fetchUnscored(int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_FETCH_LIMIT);
        List<LlmTraceEntity> rows = llmTraceMapper.selectList(unscored()
                .select(LlmTraceEntity::getId, LlmTraceEntity::getTraceId, LlmTraceEntity::getSessionId,
                        LlmTraceEntity::getAppId, LlmTraceEntity::getModelName,
                        LlmTraceEntity::getPromptContent, LlmTraceEntity::getResponseContent,
                        LlmTraceEntity::getStartTime)
                .orderByDesc(LlmTraceEntity::getStartTime)
                // limit 由上面的整数夹过，不接受外部拼接：这一句是拼进 SQL 的，不能有第二个来源
                .last("limit " + size));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, Retrieval> retrievals = loadRetrievals(rows);
        List<OnlineTraceSample> samples = new ArrayList<>(rows.size());
        for (LlmTraceEntity row : rows) {
            Retrieval retrieval = retrievals.get(row.getTraceId());
            List<ContextChunk> chunks = retrieval == null ? List.of() : retrieval.chunks();
            String query = retrieval == null ? null : retrieval.query();
            samples.add(new OnlineTraceSample(row.getId(), row.getTraceId(), row.getSessionId(),
                    row.getAppId(), row.getModelName(),
                    StringUtils.hasText(query) ? query : lastUserText(row.getPromptContent()),
                    row.getResponseContent(), chunks, row.getStartTime()));
        }
        return samples;
    }

    @Override
    public long countUnscored() {
        Long count = llmTraceMapper.selectCount(unscored());
        return count == null ? 0L : count;
    }

    /**
     * 待评样本的共用条件：成功 + 没标过 + 不是评测自己发起的调用。
     * <p>
     * 取数与计数必须用同一个条件，否则面板上的积压水位永远降不下去（数得到、捞不着）；
     * traceId 为空的行要留在待评里（它们只是没挂上链路上下文），所以判空那一支不能漏。
     */
    private LambdaQueryWrapper<LlmTraceEntity> unscored() {
        return new LambdaQueryWrapper<LlmTraceEntity>()
                .eq(LlmTraceEntity::getStatus, STATUS_SUCCESS)
                .eq(LlmTraceEntity::getEvaluated, Boolean.FALSE)
                .and(wrapper -> wrapper.isNull(LlmTraceEntity::getTraceId)
                        .or().notLikeRight(LlmTraceEntity::getTraceId, EVAL_TRACE_PREFIX));
    }

    @Override
    public boolean markScored(String id, Map<String, Double> scores) {
        if (!StringUtils.hasText(id)) {
            return false;
        }
        String json = null;
        if (scores != null && !scores.isEmpty()) {
            try {
                json = JsonUtils.toJSONString(scores);
            } catch (Exception e) {
                // 分数是指标自己产出的 map，序列化不了说明上游有脏值；这一条按没写下去处理，等下一轮
                log.warn("在线评测分数序列化失败: traceId={}, {}", id, e.toString());
                return false;
            }
        }
        // 带着「仍未被标过」的条件写：定时任务与手动触发撞上时，只有一边能写成功，
        // 同一条 trace 不会被评两遍、白烧一次判定 token
        LambdaUpdateWrapper<LlmTraceEntity> wrapper = new LambdaUpdateWrapper<LlmTraceEntity>()
                .set(LlmTraceEntity::getEvaluated, Boolean.TRUE)
                .set(LlmTraceEntity::getEvalScores, json)
                .set(LlmTraceEntity::getUpdateTime, LocalDateTime.now())
                .eq(LlmTraceEntity::getId, id)
                .eq(LlmTraceEntity::getEvaluated, Boolean.FALSE);
        return llmTraceMapper.update(null, wrapper) == 1;
    }

    /**
     * 一条链路的检索留痕：问题 + 当次召回的片段。
     * <p>
     * Agent 一次执行可能检索多次，片段按发生顺序合并并按段落去重 —— 指标关心的是
     * 「模型回答时能看到哪些内容」，多次检索的并集才是那一份上下文。
     */
    private record Retrieval(String query, List<ContextChunk> chunks) {
    }

    private Map<String, Retrieval> loadRetrievals(List<LlmTraceEntity> rows) {
        Set<String> traceIds = new LinkedHashSet<>();
        for (LlmTraceEntity row : rows) {
            if (StringUtils.hasText(row.getTraceId())) {
                traceIds.add(row.getTraceId());
            }
        }
        if (traceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TraceRecordEntity> spans = traceRecordMapper.selectList(new LambdaQueryWrapper<TraceRecordEntity>()
                .select(TraceRecordEntity::getTraceId, TraceRecordEntity::getInputPayload,
                        TraceRecordEntity::getOutputPayload)
                .eq(TraceRecordEntity::getModule, MODULE_RETRIEVAL)
                .eq(TraceRecordEntity::getAction, ACTION_SEARCH)
                .in(TraceRecordEntity::getTraceId, traceIds)
                .orderByAsc(TraceRecordEntity::getStartTime));
        if (spans == null || spans.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, RetrievalAccumulator> accumulators = new LinkedHashMap<>();
        for (TraceRecordEntity span : spans) {
            accumulators.computeIfAbsent(span.getTraceId(), key -> new RetrievalAccumulator())
                    .accept(span);
        }
        Map<String, Retrieval> byTrace = new LinkedHashMap<>(accumulators.size());
        accumulators.forEach((traceId, accumulator) -> byTrace.put(traceId, accumulator.toRetrieval()));
        return byTrace;
    }

    /** 同一条链路的多次检索往一处并，段落去重（没有段落 ID 的按正文去重） */
    private static final class RetrievalAccumulator {
        private String query;
        private final List<ContextChunk> chunks = new ArrayList<>();
        private final Set<String> seen = new LinkedHashSet<>();

        void accept(TraceRecordEntity span) {
            if (query == null) {
                query = readQuery(span.getInputPayload());
            }
            for (ContextChunk chunk : readChunks(span.getOutputPayload())) {
                String key = StringUtils.hasText(chunk.paragraphId()) ? chunk.paragraphId() : chunk.text();
                if (key != null && !key.isBlank() && seen.add(key)) {
                    chunks.add(chunk);
                }
            }
        }

        Retrieval toRetrieval() {
            return new Retrieval(query, List.copyOf(chunks));
        }

        /**
         * 检索输入里的原始 query 就是用户那一问；改写后的口径只在没给原文时兜底。
         */
        private static String readQuery(String inputPayload) {
            if (!StringUtils.hasText(inputPayload)) {
                return null;
            }
            try {
                JsonNode node = JsonUtils.parseObject(inputPayload);
                String query = text(node, "query");
                return StringUtils.hasText(query) ? query.trim() : trimOrNull(text(node, "effectiveQuery"));
            } catch (Exception e) {
                return null;
            }
        }

        private static List<ContextChunk> readChunks(String outputPayload) {
            if (!StringUtils.hasText(outputPayload)) {
                return List.of();
            }
            try {
                ArrayNode array = JsonUtils.parseArray(outputPayload);
                List<ContextChunk> chunks = new ArrayList<>(array.size());
                array.forEach(item -> {
                    String content = text(item, "content");
                    if (StringUtils.hasText(content)) {
                        chunks.add(new ContextChunk(content.trim(), text(item, "paragraphId"),
                                text(item, "documentId")));
                    }
                });
                return chunks;
            } catch (Exception e) {
                // 载荷是历史数据，脏一条不能让整批样本一起取不出来
                log.debug("检索载荷解析失败，本条样本按无片段处理: {}", e.toString());
                return List.of();
            }
        }
    }

    /**
     * 没检索 span（或载荷开关关着）时，从 prompt 里退一步取最后一条用户消息当问题。
     * <p>
     * 取「最后一条」而不是「第一条」：多轮对话里第一条往往是寒暄，被判定的那一轮问的是最后一句。
     */
    private static String lastUserText(String promptContent) {
        if (!StringUtils.hasText(promptContent)) {
            return null;
        }
        try {
            ArrayNode messages = JsonUtils.parseArray(promptContent);
            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonNode message = messages.get(i);
                String type = text(message, "type") == null ? text(message, "role") : text(message, "type");
                if (type == null || !type.toLowerCase().contains("user")) {
                    continue;
                }
                String plain = text(message, "text");
                if (!StringUtils.hasText(plain)) {
                    plain = text(message, "content");
                }
                if (!StringUtils.hasText(plain)) {
                    // 新版 langchain4j 把内容拆成 contents 数组（图文混排），纯文本段拼回去
                    plain = joinContents(message.get("contents"));
                }
                if (StringUtils.hasText(plain)) {
                    return plain.trim();
                }
            }
        } catch (Exception e) {
            log.debug("prompt 解析失败，本条样本按无问题处理: {}", e.toString());
        }
        return null;
    }

    private static String joinContents(JsonNode contents) {
        if (contents == null || !contents.isArray()) {
            return null;
        }
        StringBuilder merged = new StringBuilder();
        contents.forEach(item -> {
            String text = text(item, "text");
            if (StringUtils.hasText(text)) {
                if (merged.length() > 0) {
                    merged.append('\n');
                }
                merged.append(text);
            }
        });
        return merged.length() == 0 ? null : merged.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        // 只在这里统一取值：JsonNode 的 toString 会带上 JSON 引号，另写一份就会把问题与片段
        // 连引号一起送进指标正文 —— 多一对引号就是一处脏数据，均值也跟着歪
        return value.isTextual() ? value.asText() : String.valueOf(value);
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}
