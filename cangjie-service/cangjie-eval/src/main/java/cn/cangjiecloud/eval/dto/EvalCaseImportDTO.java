package cn.cangjiecloud.eval.dto;

import lombok.Data;

import java.util.List;

/**
 * 线上对话回流评测集的入参。
 * <p>
 * 三个接口共用一个形状：单条回流只看 {@code messageId}，勾选回流看 {@code messageIds}，
 * 剩下的批量回流看筛选条件。字段全部可空 = 不限，空串与 null 同义（前端表单清空后交上来的是空串）。
 */
@Data
public class EvalCaseImportDTO {

    /** 单条回流：对话消息 ID（AI 回复或其对应的用户提问都可以） */
    private String messageId;

    /**
     * 勾选回流：本次选中的消息 ID，非空时按 ID 精确取样本，筛选条件一律忽略。
     * <p>
     * 后台勾的是「就这几条」，把它翻译成筛选条件等于让运维者猜服务端会多捞什么。
     */
    private List<String> messageIds;

    /** 批量回流：限定应用，空表示全部应用 */
    private String applicationId;

    /** 批量回流：限定反馈 like / dislike / none，空表示不限 */
    private String feedback;

    /** 批量回流：只要有人工标注的（true）/ 只要没人工标注的（false）/ 不限（null） */
    private Boolean annotatedOnly;

    /** 批量回流：本次最多扫多少条，服务端另有硬上限 */
    private Integer limit;
}
