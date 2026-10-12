package cn.cangjiecloud.core.conversation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条线上对话样本：问题 + AI 回答 + 用户反馈 + 人工标注 + 当时的检索来源。
 * <p>
 * 这是「线上反馈回流成评测集」的中性载体。评测模块不依赖对话模块，编译期只认这个记录，
 * 由对话侧把 {@code chat_message} 拆成问答对再交出来 —— 免得为了取一条消息把 ORM 拖进 SPI。
 * <p>
 * 问答配对由实现方负责（{@code question} 取该回答之前最近的一条用户消息）：让调用方自己去翻
 * 会话顺序，每个消费方都会重写一遍同样的逻辑，还容易配错轮次。
 *
 * @param messageId       AI 消息 ID（回流的幂等键）
 * @param sessionId       会话 ID
 * @param applicationId   应用 ID
 * @param question        配对的用户问题，取不到时为 null
 * @param answer          AI 回答原文
 * @param history         这一轮之前的对话（按时间升序，不含本问答），多轮追问靠它还原语境
 * @param feedback        用户反馈：like / dislike / none，可为 null
 * @param annotation      人工标注的修正答案（有它就以它为准），可为 null
 * @param referenceDocs   当时召回命中的文档 ID（去重），可为空列表
 * @param referenceChunks 当时召回命中的片段 ID（去重），可为空列表
 * @param createTime      消息时间
 */
public record ConversationSample(String messageId,
                                 String sessionId,
                                 String applicationId,
                                 String question,
                                 String answer,
                                 List<ConversationTurn> history,
                                 String feedback,
                                 String annotation,
                                 List<String> referenceDocs,
                                 List<String> referenceChunks,
                                 LocalDateTime createTime) {

    public ConversationSample {
        // 列表来自脏 JSON，null 元素得先清掉：List.copyOf 不容 null，而这里一个 null 不值得抛异常
        history = history == null ? List.of()
                : history.stream().filter(t -> t != null && t.content() != null && !t.content().isBlank()).toList();
        referenceDocs = nonBlank(referenceDocs);
        referenceChunks = nonBlank(referenceChunks);
    }

    private static List<String> nonBlank(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    }

    /** 期望答案：人工标注优先，没有标注就没有标准答案（仍可用来测检索指标） */
    public String expectedAnswer() {
        return annotation == null || annotation.isBlank() ? null : annotation;
    }

    /** 能不能当评测用例：至少得有个问题 */
    public boolean usable() {
        return question != null && !question.isBlank();
    }
}
