package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 评估结果明细（一条 case 一行）
 * <p>
 * 此前逐 case 结果整包塞在 {@code eval_run.results} 里：每跑完一个 case 都要把整行重写一遍，
 * 结果越多写得越慢（平方级膨胀），而且跨 run 的指标趋势、同用例回归对比都得先把 JSON
 * 捞回内存再拆。拆成明细表后只 insert 不 update，已知指标用真实数值列，能直接 SQL 聚合。
 * <p>
 * 分数列的可空是有语义的：null = 本次没评这个指标，不能当 0 计入均值 —— 把「没出分」算成
 * 「零分」会凭空拉低整体水位，这正是过去 correctness 均值不可用的原因之一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "eval_result", autoResultMap = true)
public class EvalResultEntity extends BaseEntity {

    /**
     * 正文重列：一条 case 的回答 + 检索片段 + 判定中间量，单条可达数 KB。
     * <p>
     * 列表接口一律不选这三列 —— 前端跑动中每几秒轮询一次明细，上百 case 全量重传正文
     * 就是每次 MB 级响应；展开某一行时才按 id 单条拉（与 trace 侧 {@code /traces/{spanId}/payload} 同一套约定）。
     */
    public static final List<String> HEAVY_COLUMNS = List.of("answer", "contexts", "detail");

    /** 所属评估运行 ID */
    private String runId;

    /** 评估用例 ID（跨 run 的同用例回归对比靠它对齐） */
    private String caseId;

    /** 数据集 ID（按数据集聚合趋势时先裁剪扫描量） */
    private String datasetId;

    /** 问题快照（用例后续被改动也不影响历史报告可读） */
    private String question;

    /** 模型生成回答 */
    private String answer;

    /** 本次检索片段（JSON：paragraphId / documentId / 各通道分数 / 名次） */
    private String contexts;

    /** 召回率 0~1；null 表示未评测 */
    private Double recall;

    /** LLM 判定正确性 0~1；null 表示未评测或判定失败 */
    private Double correctness;

    /** 其余指标分值（JSON map），新增指标无需改表 */
    private String extraScores;

    /** 判定依据与中间量（JSON：judge 理由 / 命中文档 / 缺失文档） */
    private String detail;

    /** 本 case 耗时（毫秒） */
    private Long latency;

    /** 输入 token 数（回答 + 判定） */
    private Long inputTokens;

    /** 输出 token 数（回答 + 判定） */
    private Long outputTokens;

    /** 总 token 数（回答 + 判定） */
    private Long totalTokens;

    /** 状态：success / partial（部分指标未出分）/ fail */
    private String status;

    /** 错误信息 */
    private String error;

    /** 本 case 模型调用的链路 ID，报告可一键跳到链路视图 */
    private String traceId;
}
