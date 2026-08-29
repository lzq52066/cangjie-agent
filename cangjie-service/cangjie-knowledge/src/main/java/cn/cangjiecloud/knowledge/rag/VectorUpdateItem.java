package cn.cangjiecloud.knowledge.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量批量回写条目（id + pgvector 文本形式向量）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorUpdateItem {

    /** 段落 ID */
    private String id;

    /** 向量文本（形如 [0.1,0.2,...]） */
    private String vector;
}
