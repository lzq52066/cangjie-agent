package cn.cangjiecloud.core.rag;

import cn.cangjiecloud.core.model.ChatMessage;

import java.util.List;

/**
 * 查询改写器
 * <p>
 * 结合对话历史对用户 query 消歧、扩写，提升多轮对话和指代场景下的检索召回率。
 */
public interface QueryRewriter {

    /** 类型标识：none / llm */
    String getType();

    /**
     * 改写用户查询
     *
     * @param originalQuery 用户原始查询
     * @param history       最近对话历史（仅 user/assistant 角色）
     * @return 改写后的检索查询；失败或空历史时原样返回
     */
    String rewrite(String originalQuery, List<ChatMessage> history);
}