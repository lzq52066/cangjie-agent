package cn.cangjiecloud.chat.harness.route;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.SpecialToolRoute;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.knowledge.rag.RetrievalToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agentic 知识库检索路由：承接 {@code search_knowledge_base}。
 * <p>
 * 改造前该函数名在对话循环里硬编码特判，这里改为路由竞争命中，使循环体不再出现工具名分支；
 * 检索失败时返回可读文本（由 {@code RetrievalToolService} 保证），与改造前回喂内容完全一致。
 */
@Component
@RequiredArgsConstructor
public class RetrievalToolRoute implements SpecialToolRoute {

    private final RetrievalToolService retrievalToolService;

    @Override
    public String name() {
        return "retrieval";
    }

    @Override
    public boolean matches(ToolInvocation invocation, HarnessContext context) {
        return RetrievalToolService.TOOL_NAME.equals(invocation.getCallName());
    }

    @Override
    public String execute(ToolInvocation invocation, HarnessContext context) {
        List<String> knowledgeBaseIds = context.getRequest() == null
                ? List.of() : context.getRequest().getKnowledgeBaseIds();
        String query = invocation.arg("query");
        return retrievalToolService.execute(query == null ? "" : query, knowledgeBaseIds);
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
