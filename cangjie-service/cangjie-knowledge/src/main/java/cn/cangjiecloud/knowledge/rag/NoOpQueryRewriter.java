package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.rag.QueryRewriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认无操作查询改写器
 * <p>
 * 原样返回用户 query，与改造前行为完全一致。
 */
@Component
public class NoOpQueryRewriter implements QueryRewriter {

    @Override
    public String getType() {
        return "none";
    }

    @Override
    public String rewrite(String originalQuery, List<ChatMessage> history) {
        return originalQuery;
    }
}