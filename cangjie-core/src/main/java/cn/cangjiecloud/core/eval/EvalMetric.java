package cn.cangjiecloud.core.eval;

/**
 * 评估指标 SPI。
 * <p>
 * 之所以做成接口而不是写死在评估流程里：RAGAS 这类框架的指标是可插拔的，
 * 加一个指标不该改评测主循环，也不该改表 —— 新指标一律落 {@code eval_result.extra_scores}，
 * 跑稳了再提升为独立列。同时在线评测（对历史对话抽样打分）要能复用同一批指标实现，
 * 所以这里只依赖 {@link EvalSample} / {@link MetricContext} 两个中性类型，不认任何 ORM 实体。
 * <p>
 * 实现约定：
 * <ul>
 *   <li>算不出来就返回 {@link MetricScore#skipped(String)}，不要造一个 0 或 0.5 顶上去；
 *       「没评测」被当成「零分」会让均值反映的不再是回答质量，而是判分接口今天稳不稳。</li>
 *   <li>外部能力缺失（没配判定模型 / 没配 embedding）用 {@link #supports} 提前挡掉，
 *       别让每次调用都靠 catch 异常来降级。</li>
 *   <li>允许抛异常，调用方按「本指标没出分」收敛，不影响同一条 case 的其它指标与后续用例。</li>
 * </ul>
 */
public interface EvalMetric {

    /**
     * 指标名，同时是 {@code extra_scores} 里的键与前端展示的列名。
     * <p>
     * 用稳定的小驼峰（如 {@code faithfulness}），改名等于历史数据读不到，要谨慎。
     */
    String name();

    /**
     * 界面展示名（可含中文），缺省回落 {@link #name()}。
     */
    default String displayName() {
        return name();
    }

    /**
     * 本指标是否必须有 ground truth 才能算。
     * <p>
     * 用于报告口径解释：没标注的用例不算这类指标是正常的，不该被标成「部分失败」。
     */
    default boolean requiresGroundTruth() {
        return false;
    }

    /**
     * 当前样本 + 当前能力下是否可评。默认「有回答就行」。
     */
    default boolean supports(EvalSample sample, MetricContext context) {
        return sample != null && sample.answer() != null && !sample.answer().isBlank();
    }

    /**
     * 打分。
     *
     * @throws Exception 判定调用失败等；调用方记日志后按未评测处理
     */
    MetricScore score(EvalSample sample, MetricContext context) throws Exception;
}
