package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiNode} 单元测试（不发起真实网络请求）：URL 缺失分支、变量占位符解析、非法 URI 异常兜底。
 */
class ApiNodeTest {

    private final ApiNode node = new ApiNode();

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeApiNode() {
        assertThat(node.getType()).isEqualTo("api");
        assertThat(node.getName()).isEqualTo("API");
        assertThat(node.getDescription()).contains("HTTP");
    }

    @Test
    // 覆盖场景：url 缺失 / 空串 / config 为 null —— 直接返回 URL 校验失败，不发请求
    void executeShouldRejectBlankUrl() {
        assertThat(node.execute(Map.of(), Map.of()))
                .containsOnlyKeys("api_success", "api_error")
                .containsEntry("api_success", false)
                .containsEntry("api_error", "URL 不能为空");
        assertThat(node.execute(Map.of(), Map.of("url", "")))
                .containsEntry("api_error", "URL 不能为空");
        assertThat(node.execute(null, null))
                .containsEntry("api_success", false);
    }

    @Test
    // 覆盖场景：url 模板变量解析后为空串 —— 仍命中"URL 不能为空"分支
    void executeShouldRejectUrlResolvedToEmpty() {
        Map<String, Object> output = node.execute(
                Map.of(),
                Map.of("url", "{missingVar}"));

        assertThat(output).containsEntry("api_error", "URL 不能为空");
    }

    @Test
    // 覆盖场景：URL 含非法字符（URI.create 抛异常）—— catch 分支返回 api_success=false
    void executeShouldCatchIllegalUri() {
        Map<String, Object> output = node.execute(
                Map.of(),
                Map.of("method", "post", "url", "http://exa mple.com/api"));

        assertThat(output).containsEntry("api_success", false);
        assertThat((String) output.get("api_error")).isNotEmpty();
    }

    @Test
    // 覆盖场景：变量替换产生相对地址（无 scheme）—— send 立即失败进入 catch，不发生外网请求
    void executeShouldCatchRelativeUriFailure() {
        Map<String, Object> output = node.execute(
                Map.of("host", "internal"),
                Map.of("url", "/{host}/api", "body", "{\"q\":\"{host}\"}"));

        assertThat(output).containsEntry("api_success", false);
        assertThat((String) output.get("api_error")).isNotEmpty();
    }
}
