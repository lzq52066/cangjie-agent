package cn.cangjiecloud.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.conversation.ConversationFilter;
import cn.cangjiecloud.core.conversation.ConversationSample;
import cn.cangjiecloud.core.conversation.ConversationSource;
import cn.cangjiecloud.core.conversation.ConversationTurn;
import cn.cangjiecloud.eval.dto.EvalCaseImportDTO;
import cn.cangjiecloud.eval.dto.EvalImportResultDTO;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.mapper.EvalCaseMapper;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalCaseServiceImpl extends ServiceImpl<EvalCaseMapper, EvalCaseEntity>
        implements IEvalCaseService {

    /** 回流用例的来源值（与 eval_case.source 列注释一致） */
    private static final String SOURCE_TRACE = "trace";

    /** 默认标签：回流用例要能单独切片看，也能与手工用例区分开 */
    private static final String TAG_FROM_CHAT = "from-chat";

    /**
     * 一次最多回流多少条勾选。
     * <p>
     * 与对话侧 {@code MAX_LIMIT} 同量级：前端一次拉不完整个会话，选中再多也是分批，
     * 上限只是把「把整张表勾上」这种误操作挡住。
     */
    private static final int MAX_SELECTED_MESSAGES = 200;

    private final IEvalDatasetService datasetService;

    /**
     * 对话样本来源：由对话模块实现，本模块不依赖它，所以运行期可选注入。
     * <p>
     * 没挂上时回流接口直接报「能力未启用」，而不是静默返空列表 —— 空列表会被读成
     * 「线上没有可回流的反馈」，而真实原因是少装了一个模块。
     */
    @Autowired(required = false)
    private ConversationSource conversationSource;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseEntity create(EvalCaseEntity entity) {
        if (!StringUtils.hasText(entity.getDatasetId())) {
            throw new ApiException("数据集 ID 不能为空");
        }
        save(entity);
        // 更新数据集用例计数
        updateCaseCount(entity.getDatasetId());
        log.info("评估用例已创建: {} ({})", entity.getQuestion(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseEntity update(String id, EvalCaseEntity entity) {
        EvalCaseEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("用例不存在");
        }
        entity.setId(id);
        entity.setDatasetId(existing.getDatasetId());
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        EvalCaseEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        updateCaseCount(entity.getDatasetId());
        log.info("评估用例已删除: {}", id);
    }

    @Override
    public IPage<EvalCaseEntity> pageQuery(String datasetId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<EvalCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCaseEntity::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalCaseEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    public List<EvalCaseEntity> listByDataset(String datasetId) {
        LambdaQueryWrapper<EvalCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCaseEntity::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalCaseEntity::getCreateTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseEntity importFromMessage(String datasetId, String messageId) {
        requireDataset(datasetId);
        requireConversationSource();
        if (!StringUtils.hasText(messageId)) {
            throw new ApiException("消息 ID 不能为空");
        }
        ConversationSample sample = conversationSource.loadSample(messageId.trim());
        if (sample == null) {
            throw new ApiException("对话消息不存在或不是可回流的回复: " + messageId);
        }
        if (!sample.usable()) {
            throw new ApiException("该消息没有配对的用户问题，无法作为评测用例");
        }
        EvalCaseEntity existing = findBySourceMessage(datasetId, sample.messageId());
        if (existing != null) {
            // 幂等：同一条 AI 消息反复点「加入数据集」只算一票，否则均值被同一件事重复投票
            return existing;
        }
        return create(toCase(datasetId, sample));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalImportResultDTO importFromConversations(String datasetId, EvalCaseImportDTO request) {
        requireDataset(datasetId);
        requireConversationSource();

        EvalCaseImportDTO condition = request == null ? new EvalCaseImportDTO() : request;
        List<String> messageIds = selectedMessageIds(condition);
        List<ConversationSample> samples = messageIds.isEmpty()
                ? conversationSource.searchSamples(new ConversationFilter(
                trimToNull(condition.getApplicationId()), trimToNull(condition.getFeedback()),
                condition.getAnnotatedOnly(), condition.getLimit()))
                // 后台勾选的是「就这几条」，按 ID 精确取，不再走筛选
                : messageIds.stream().map(conversationSource::loadSample).toList();

        Set<String> seen = existingSourceMessageIds(datasetId);
        List<EvalCaseEntity> pending = new ArrayList<>();
        int duplicated = 0;
        int unusable = 0;
        for (ConversationSample sample : samples) {
            if (sample == null || !sample.usable()) {
                // 开场白、纯客套这类回复配不到用户问题，消息被删掉也走这一支：
                // 不入库但要点名条数，不然「扫了 50 条只进来 3 条」看起来像代码漏写了
                unusable++;
                continue;
            }
            // 已回流过的按幂等跳过；本批内重复命中的也一并计入 seen
            if (!seen.add(sample.messageId())) {
                duplicated++;
                continue;
            }
            pending.add(toCase(datasetId, sample));
        }
        if (!pending.isEmpty()) {
            saveBatch(pending);
            // 计数只刷一次：逐条 create 会把「导 50 条」做成 50 次 count + 50 次数据集回写
            updateCaseCount(datasetId);
        }

        List<String> caseIds = pending.stream().map(EvalCaseEntity::getId).toList();
        log.info("对话回流完成: dataset={}, 扫={} 新增={} 重复={} 不可用={}",
                datasetId, samples.size(), pending.size(), duplicated, unusable);
        return EvalImportResultDTO.builder()
                .scanned(samples.size())
                .imported(pending.size())
                .duplicated(duplicated)
                .unusable(unusable)
                .caseIds(caseIds)
                .build();
    }

    /**
     * 回流样本 → 用例。
     * <p>
     * 当时的检索来源直接当成 ground truth：它们是模型真的拿去回答的那批片段，
     * 比事后人工补标更贴近「重现这次回答需要召回到什么」。
     */
    private EvalCaseEntity toCase(String datasetId, ConversationSample sample) {
        EvalCaseEntity entity = new EvalCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setQuestion(sample.question().trim());
        entity.setExpectedAnswer(sample.expectedAnswer());
        entity.setConversation(toConversationJson(sample.history()));
        entity.setReferenceDocs(toIdJson(sample.referenceDocs()));
        entity.setReferenceChunks(toIdJson(sample.referenceChunks()));
        // 标签带上反馈：点踩的那批修没修好，是这套闭环唯一的验收口径，得能单独切片看
        String feedback = trimToNull(sample.feedback());
        entity.setTags(feedback == null || "none".equals(feedback) ? TAG_FROM_CHAT : TAG_FROM_CHAT + "," + feedback);
        entity.setSource(SOURCE_TRACE);
        entity.setSourceMessageId(sample.messageId());
        return entity;
    }

    /** 空列表写 null：一个 "[]" 与「没有标注」在报告里应该是同一个样子 */
    private static String toIdJson(List<String> ids) {
        return ids == null || ids.isEmpty() ? null : JsonUtils.toJSONString(ids);
    }

    /** 勾选的消息 ID：去空、去重、夹到上限，空列表表示「这次按筛选条件批量回流」 */
    private static List<String> selectedMessageIds(EvalCaseImportDTO condition) {
        List<String> ids = condition.getMessageIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_SELECTED_MESSAGES)
                .toList();
    }

    /** 单轮问答没有上文，这一列留 null：报告里「不带历史」与「历史为空」是同一件事 */
    private static String toConversationJson(List<ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        List<Map<String, String>> turns = history.stream()
                .map(turn -> {
                    // 固定 role 在前、content 在后：LinkedHashMap 保序，落库的 JSON 与前端读到的顺序一致
                    Map<String, String> turn0 = new LinkedHashMap<>(2);
                    turn0.put("role", StringUtils.hasText(turn.role()) ? turn.role() : "user");
                    turn0.put("content", turn.content());
                    return turn0;
                })
                .toList();
        return JsonUtils.toJSONString(turns);
    }

    private EvalCaseEntity findBySourceMessage(String datasetId, String messageId) {
        return getOne(new LambdaQueryWrapper<EvalCaseEntity>()
                .eq(EvalCaseEntity::getDatasetId, datasetId)
                .eq(EvalCaseEntity::getSourceMessageId, messageId)
                .last("LIMIT 1"), false);
    }

    /** 本数据集已回流过的消息 ID：一次查完，不逐条 exists */
    private Set<String> existingSourceMessageIds(String datasetId) {
        List<EvalCaseEntity> existing = list(new LambdaQueryWrapper<EvalCaseEntity>()
                .select(EvalCaseEntity::getSourceMessageId)
                .eq(EvalCaseEntity::getDatasetId, datasetId)
                .isNotNull(EvalCaseEntity::getSourceMessageId));
        Set<String> ids = new HashSet<>();
        for (EvalCaseEntity entity : existing) {
            if (entity.getSourceMessageId() != null) {
                ids.add(entity.getSourceMessageId());
            }
        }
        return ids;
    }

    private void requireDataset(String datasetId) {
        if (datasetService.getById(datasetId) == null) {
            throw new ApiException("数据集不存在: " + datasetId);
        }
    }

    private void requireConversationSource() {
        if (conversationSource == null) {
            throw new ApiException("对话回流能力未启用：未装配对话模块");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updateCaseCount(String datasetId) {
        long count = count(new LambdaQueryWrapper<EvalCaseEntity>()
                .eq(EvalCaseEntity::getDatasetId, datasetId));
        EvalDatasetEntity ds = new EvalDatasetEntity();
        ds.setId(datasetId);
        ds.setCaseCount((int) count);
        datasetService.updateById(ds);
    }
}