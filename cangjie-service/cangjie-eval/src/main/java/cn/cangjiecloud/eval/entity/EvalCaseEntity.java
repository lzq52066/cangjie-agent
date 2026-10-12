package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评估用例实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eval_case")
public class EvalCaseEntity extends BaseEntity {

    /** 所属数据集 ID */
    private String datasetId;

    /** 问题 */
    private String question;

    /** 期望回答 */
    private String expectedAnswer;

    /**
     * 这一轮之前的对话（JSON 数组 {@code [{"role":"user","content":"..."}]}），可空。
     * <p>
     * 线上回流带得出来：「那第二个呢」这类追问单独摆着没法评，跑离线评测时要把上文一起喂进去，
     * 模型才可能重现当时那次回答。
     */
    private String conversation;

    /** 参考文档 ID 列表（JSON 数组，用于召回率计算） */
    private String referenceDocs;

    /**
     * 参考片段 ID 列表（JSON 数组）：片段级 ground truth。
     * <p>
     * 只有文档级标注时，「文档找到了、答题那段没进上下文」照样算满分，
     * chunk 切分与 topK 的问题全被掩盖；contextRecall（片段口径）要靠这一列。
     */
    private String referenceChunks;

    /** 标签（逗号分隔），用于按主题切片看指标 */
    private String tags;

    /** 难度：easy / medium / hard，可空 */
    private String difficulty;

    /** 用例来源：manual 手工 / trace 从对话回流 / synthetic 合成 */
    private String source;

    /**
     * 回流来源消息 ID。
     * <p>
     * 同一条 AI 消息重复点「加入数据集」时靠它幂等跳过，否则一个坏 case 会被灌进去 N 份，
     * 均值被同一件事反复投票。
     */
    private String sourceMessageId;
}