package cn.cangjiecloud.tool.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.service.ToolServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ToolProviderServiceImpl 单元测试
 * <p>
 * 验证工具规格构建（类型映射 + 异常隔离）与 function calling 调用的分发逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工具提供者服务实现 - ToolProviderServiceImpl")
class ToolProviderServiceImplTest {

    @Mock
    private ToolHandlerRegistry handlerRegistry;
    @Mock
    private ToolServiceImpl toolService;
    @Mock
    private AbsToolHandler httpHandler;
    @Mock
    private AbsToolHandler pluginHandler;

    private ToolProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ToolProviderServiceImpl(handlerRegistry, toolService);
    }

    /**
     * 构造工具实体
     */
    private static ToolEntity tool(String id, String type, String status) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setName("工具-" + id);
        entity.setType(type);
        entity.setStatus(status);
        return entity;
    }

    /**
     * 构造带调用名的规格
     */
    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder().name(name).build();
    }

    @Nested
    @DisplayName("getToolSpecifications - 构建工具规格")
    class GetToolSpecifications {

        /**
         * 入参为空时直接返回空列表，不访问数据库
         */
        @Test
        void shouldReturnEmptyForEmptyIds() {
            assertThat(service.getToolSpecifications(null)).isEmpty();
            assertThat(service.getToolSpecifications(List.of())).isEmpty();
            verifyNoInteractions(toolService, handlerRegistry);
        }

        /**
         * 数据库查不到工具时返回空列表
         */
        @Test
        void shouldReturnEmptyWhenToolsMissing() {
            when(toolService.listByIds(anyList())).thenReturn(List.of());

            assertThat(service.getToolSpecifications(List.of("a", "b"))).isEmpty();
            verifyNoInteractions(handlerRegistry);
        }

        /**
         * 未激活的工具被过滤，不参与规格构建
         */
        @Test
        void shouldFilterInactiveTools() {
            ToolEntity inactive = tool("t2", "HTTP", ToolConstants.STATUS_INACTIVE);
            when(toolService.listByIds(anyList())).thenReturn(List.of(inactive));

            assertThat(service.getToolSpecifications(List.of("t2"))).isEmpty();
            verifyNoInteractions(handlerRegistry);
        }

        /**
         * 按类型分组逐个构建规格，同类型工具复用同一处理器
         */
        @Test
        void shouldBuildSpecificationPerTool() {
            ToolEntity first = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            ToolEntity second = tool("t2", "HTTP", ToolConstants.STATUS_ACTIVE);
            when(toolService.listByIds(anyList())).thenReturn(List.of(first, second));
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(httpHandler.buildToolSpecification(first)).thenReturn(spec("fn_1"));
            when(httpHandler.buildToolSpecification(second)).thenReturn(spec("fn_2"));

            List<ToolSpecification> result = service.getToolSpecifications(List.of("t1", "t2"));

            assertThat(result).extracting(ToolSpecification::getName).containsExactly("fn_1", "fn_2");
        }

        /**
         * 类型没有对应处理器时跳过该分组
         */
        @Test
        void shouldSkipGroupWithoutHandler() {
            ToolEntity entity = tool("t1", "UNKNOWN", ToolConstants.STATUS_ACTIVE);
            when(toolService.listByIds(anyList())).thenReturn(List.of(entity));
            when(handlerRegistry.get(ToolConstants.ToolType.PLUGIN)).thenReturn(null);

            assertThat(service.getToolSpecifications(List.of("t1"))).isEmpty();
        }

        /**
         * 单个工具构建失败不影响其余工具
         */
        @Test
        void shouldIsolateBuildFailure() {
            ToolEntity broken = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            ToolEntity healthy = tool("t2", "HTTP", ToolConstants.STATUS_ACTIVE);
            when(toolService.listByIds(anyList())).thenReturn(List.of(broken, healthy));
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(httpHandler.buildToolSpecification(broken)).thenThrow(new IllegalStateException("参数非法"));
            when(httpHandler.buildToolSpecification(healthy)).thenReturn(spec("fn_2"));

            List<ToolSpecification> result = service.getToolSpecifications(List.of("t1", "t2"));

            assertThat(result).extracting(ToolSpecification::getName).containsExactly("fn_2");
        }

        /**
         * 旧 type 字段映射到新类型：http 走 HTTP，空类型走 PLUGIN
         */
        @Test
        void shouldMapLegacyTypeToHandler() {
            ToolEntity legacyHttp = tool("t1", "http", ToolConstants.STATUS_ACTIVE);
            ToolEntity noType = tool("t2", null, ToolConstants.STATUS_ACTIVE);
            when(toolService.listByIds(anyList())).thenReturn(List.of(legacyHttp, noType));
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(handlerRegistry.get(ToolConstants.ToolType.PLUGIN)).thenReturn(pluginHandler);
            when(httpHandler.buildToolSpecification(legacyHttp)).thenReturn(spec("fn_http"));
            when(pluginHandler.buildToolSpecification(noType)).thenReturn(spec("fn_plugin"));

            List<ToolSpecification> result = service.getToolSpecifications(List.of("t1", "t2"));

            assertThat(result).extracting(ToolSpecification::getName)
                    .containsExactlyInAnyOrder("fn_http", "fn_plugin");
        }
    }

    @Nested
    @DisplayName("executeToolCall - function calling 调用")
    class ExecuteToolCall {

        /**
         * 函数名反查不到工具时返回错误 JSON
         */
        @Test
        void shouldReturnErrorWhenToolMissing() {
            when(toolService.resolveByCallName("no_such_fn")).thenReturn(null);

            String json = service.executeToolCall("no_such_fn", Map.of());

            assertThat(JsonUtils.parseMap(json))
                    .containsEntry("success", false)
                    .containsEntry("error", "工具不存在: no_such_fn");
            verifyNoInteractions(handlerRegistry);
        }

        /**
         * 工具类型没有处理器时返回不支持提示
         */
        @Test
        void shouldReturnErrorWhenHandlerMissing() {
            ToolEntity entity = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            when(toolService.resolveByCallName("fn_1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(null);

            String json = service.executeToolCall("fn_1", Map.of());

            assertThat(JsonUtils.parseMap(json)).containsEntry("error", "不支持的工具类型");
        }

        /**
         * 执行成功时直接返回输出文本
         */
        @Test
        void shouldReturnOutputWhenSuccess() {
            ToolEntity entity = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            Map<String, Object> arguments = Map.of("q", "hello");
            when(toolService.resolveByCallName("fn_1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(httpHandler.execute(entity, arguments)).thenReturn(ToolExecuteResultDTO.builder()
                    .success(true).output("响应内容").build());

            assertThat(service.executeToolCall("fn_1", arguments)).isEqualTo("响应内容");
        }

        /**
         * 执行成功但输出为空时返回空字符串
         */
        @Test
        void shouldReturnEmptyStringWhenOutputNull() {
            ToolEntity entity = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            when(toolService.resolveByCallName("fn_1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(httpHandler.execute(any(), any())).thenReturn(ToolExecuteResultDTO.builder()
                    .success(true).output(null).build());

            assertThat(service.executeToolCall("fn_1", Map.of())).isEmpty();
        }

        /**
         * 执行失败时包装错误 JSON
         */
        @Test
        void shouldReturnErrorJsonWhenFailure() {
            ToolEntity entity = tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE);
            when(toolService.resolveByCallName("fn_1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(httpHandler);
            when(httpHandler.execute(any(), any())).thenReturn(ToolExecuteResultDTO.builder()
                    .success(false).error("请求超时").build());

            String json = service.executeToolCall("fn_1", Map.of());

            assertThat(JsonUtils.parseMap(json))
                    .containsEntry("success", false)
                    .containsEntry("error", "请求超时");
        }
    }

    @Nested
    @DisplayName("destroy 场景 - 处理器注册表为空")
    class NoHandlerRegistry {

        /**
         * 注册表返回 null 时不触碰其它依赖，直接返回空列表
         */
        @Test
        void shouldNotCallToolServiceTwice() {
            when(toolService.listByIds(anyList())).thenReturn(List.of(tool("t1", "HTTP", ToolConstants.STATUS_ACTIVE)));
            when(handlerRegistry.get(any())).thenReturn(null);

            assertThat(service.getToolSpecifications(List.of("t1"))).isEmpty();
            verify(toolService, never()).getToolSpecifications(anyList());
        }
    }
}
