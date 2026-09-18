package cn.cangjiecloud.prompt.entity;

import lombok.Data;

/**
 * 记忆向量近邻查询结果：已有记忆与待写入记忆的语义相似度
 */
@Data
public class MemorySimilarity {

    /** 已有记忆 ID */
    private String id;

    /** 已有记忆内容 */
    private String content;

    /** 余弦相似度（0~1，越大越相似） */
    private Double similarity;
}
