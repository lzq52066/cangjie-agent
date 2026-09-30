package cn.cangjiecloud.chat.harness.route;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.knowledge.rag.RetrievalToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RetrievalToolRoute} 单元测试：覆盖路由名称、匹配规则、顺序，
 * 以及执行时知识库 ID 与 query 参数的透传与缺失回落。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalToolRouteTest {

    /** 检索工具服务桩 */
    @Mock
    private RetrievalToolService retrievalToolService;

    /** 被测试检索路由 */
    private RetrievalToolRoute route;

    @BeforeEach
    void setUp() {
        route = new RetrievalToolRoute(retrievalToolService);
    }

    /** 路由名称为 retrieval，顺序为 100 */
    @Test
    void shouldExposeNameAndOrder() {
        assertThat(route.name()).isEqualTo("retrieval");
        assertThat(route.getOrder()).isEqualTo(100);
    }

    /** 仅当函数名等于 search_knowledge_base 时匹配 */
    @Test
    void shouldMatchOnlyRetrievalToolName() {
        ToolInvocation retrieval = ToolInvocation.builder()
                .callName(RetrievalToolService.TOOL_NAME).build();
        ToolInvocation other = ToolInvocation.builder().callName("other").build();

        assertThat(route.matches(retrieval, null)).isTrue();
        assertThat(route.matches(other, null)).isFalse();
    }

    /** 执行时把 query 与请求中的知识库 ID 透传给检索服务 */
    @Test
    void shouldDelegateWithQueryAndKbIds() {
        List<String> kbIds = List.of("kb-1", "kb-2");
        HarnessContext context = new HarnessContext(
                HarnessRequest.builder().knowledgeBaseIds(kbIds).build());
        ToolInvocation invocation = ToolInvocation.builder()
                .arguments(Map.of("query", "你好"))
                .build();
        when(retrievalToolService.execute(eq("你好"), eq(kbIds))).thenReturn("检索结果");

        String result = route.execute(invocation, context);

        assertThat(result).isEqualTo("检索结果");
        verify(retrievalToolService).execute("你好", kbIds);
    }

    /** query 参数缺失时以空字符串透传，知识库 ID 缺失时以 null 透传 */
    @Test
    void shouldUseEmptyQueryAndKbIdsWhenMissing() {
        HarnessContext context = new HarnessContext(HarnessRequest.builder().build());
        ToolInvocation invocation = ToolInvocation.builder().arguments(Map.of()).build();
        when(retrievalToolService.execute(eq(""), isNull())).thenReturn("空结果");

        String result = route.execute(invocation, context);

        assertThat(result).isEqualTo("空结果");
        verify(retrievalToolService).execute("", null);
    }
}
