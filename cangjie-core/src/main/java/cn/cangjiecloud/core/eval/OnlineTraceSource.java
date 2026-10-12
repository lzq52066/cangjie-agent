package cn.cangjiecloud.core.eval;

import java.util.List;
import java.util.Map;

/**
 * 线上调用样本的来源与回填出口。
 * <p>
 * 样本存在可观测性模块（{@code llm_trace} + 链路 span），打分能力存在评测模块（指标 + 判定模型），
 * 两者互相都不依赖对方，因此这条通道只能以 core 接口为编译期契约、由 Spring 在运行期拼起来。
 * <p>
 * 分数就地回写到调用记录上而不另起一张表：趋势查询由此可以直接聚合 {@code llm_trace}，
 * 不必为了看一条曲线先把两张表 join 回来。
 */
public interface OnlineTraceSource {

    /**
     * 捞一批「已成功、还没打过标」的线上调用，新的在前。
     *
     * @param limit 最多取多少条，实现方按需夹到安全上限
     * @return 样本列表，没有待评样本时为空列表而不是 null
     */
    List<OnlineTraceSample> fetchUnscored(int limit);

    /**
     * 还有多少条没打过标（面板上的积压水位；只算成功的调用，失败的不值得评）。
     */
    long countUnscored();

    /**
     * 回填一条样本的分数并标记为已评。
     * <p>
     * 带「仍未被标过」的条件：并发调度（定时任务与手动触发撞上）时后写的一次不该覆盖先写的分数，
     * 更不该让同一条 trace 被评两遍、白烧一次判定 token。
     *
     * @param id     样本 ID
     * @param scores 指标名 → 0~1 分数，只放真正出了分的那些；空 map 表示一个指标都没出
     * @return 是否真的写下去了（false = 这条已被别人抢标或不存在）
     */
    boolean markScored(String id, Map<String, Double> scores);
}
