package cn.cangjiecloud.eval.dto;

import lombok.Data;

import java.util.List;

/**
 * 在线评测触发参数（全部可空）。
 */
@Data
public class OnlineEvalRequestDTO {

    /**
     * 本轮最多评多少条，默认由服务端给定。
     * <p>
     * 默认值刻意偏小：在线评测每条样本要跑 2~4 次判定调用，「评多少」直接等于「花多少钱」，
     * 该由调用方明确决定，而不是被一个默认值替它决定。
     */
    private Integer limit;

    /** 判定模型 ID；不传则取系统里第一个可用的对话模型 */
    private String judgeModelId;

    /** 只评这几个指标；不传则全跑（不支持的指标会自己跳过） */
    private List<String> metrics;
}
