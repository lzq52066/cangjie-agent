package cn.cangjiecloud.core.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本切片结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextChunk {

    /** 切片内容 */
    private String content;

    /** 所属文档标题/来源 */
    private String source;

    /** 页码（PDF等文档适用） */
    private Integer pageNumber;

    /** 切片在原文中的字符偏移量 */
    private Integer startOffset;

    /** 切片序号 */
    private Integer chunkIndex;
}
