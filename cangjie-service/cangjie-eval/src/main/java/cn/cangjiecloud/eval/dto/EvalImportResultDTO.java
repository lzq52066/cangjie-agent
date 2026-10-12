package cn.cangjiecloud.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量回流的执行结果。
 * <p>
 * 分四栏报数而不是只给一个「成功 N 条」：重复与配不上问题都是用户点「回流」后什么都没发生的
 * 真实原因，混成一句「导入完成」就没人知道数据集为什么不涨。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalImportResultDTO {

    /** 扫到多少条候选对话消息 */
    private int scanned;

    /** 实际新增多少条用例 */
    private int imported;

    /** 已经回流过、按幂等跳过的条数 */
    private int duplicated;

    /** 配不到用户问题、无法当用例的条数 */
    private int unusable;

    /** 新增用例的 ID，供前端点进去核对（只回 ID 不回整包内容，批量导入不该传一堆正文） */
    private List<String> caseIds;
}
