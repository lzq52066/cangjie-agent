package cn.cangjiecloud.tool.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolExecuteServiceImpl 单元测试
 * <p>
 * 验证按 toolType 策略分发：激活校验、处理器查找、脚本临时注入。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工具执行服务实现 - ToolExecuteServiceImpl")
class ToolExecuteServiceImplTest {

    @Mock
    private ToolHandlerRegistry handlerRegistry;
    @Mock
    private ToolServiceImpl toolService;
    @Mock
    private AbsToolHandler handler;

    private ToolExecuteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ToolExecuteServiceImpl(handlerRegistry, toolService);
    }

    /**
     * 构造激活状态的工具
     */
    private static ToolEntity activeTool(String id) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setName("工具-" + id);
        entity.setStatus(ToolConstants.STATUS_ACTIVE);
        return entity;
    }

    /**
     * 构造处理器返回结果
     */
    private static ToolExecuteResultDTO result(String output) {
        return ToolExecuteResultDTO.builder().success(true).output(output).build();
    }

    @Nested
    @DisplayName("httpExecute - HTTP 工具执行")
    class HttpExecute {

        /**
         * 工具不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingTool() {
            when(toolService.getById("t404")).thenReturn(null);

            assertThatThrownBy(() -> service.httpExecute("t404", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具不存在: t404");
        }

        /**
         * 工具未激活时抛出业务异常
         */
        @Test
        void shouldRejectInactiveTool() {
            ToolEntity entity = activeTool("t1");
            entity.setStatus(ToolConstants.STATUS_INACTIVE);
            when(toolService.getById("t1")).thenReturn(entity);

            assertThatThrownBy(() -> service.httpExecute("t1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具未激活: t1");
        }

        /**
         * 没有对应类型的处理器时抛出业务异常
         */
        @Test
        void shouldRejectMissingHandler() {
            when(toolService.getById("t1")).thenReturn(activeTool("t1"));
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(null);

            assertThatThrownBy(() -> service.httpExecute("t1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("不支持的工具类型: " + ToolConstants.ToolType.HTTP);
            verify(handler, never()).execute(any(), anyMap());
        }

        /**
         * 正常分发到 HTTP 处理器并透传结果
         */
        @Test
        void shouldDispatchToHttpHandler() {
            ToolEntity entity = activeTool("t1");
            Map<String, Object> params = Map.of("q", "hello");
            when(toolService.getById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(handler);
            when(handler.execute(entity, params)).thenReturn(result("http-ok"));

            ToolExecuteResultDTO executed = service.httpExecute("t1", params);

            assertThat(executed.getOutput()).isEqualTo("http-ok");
        }
    }

    @Nested
    @DisplayName("customExecute - 自定义脚本执行")
    class CustomExecute {

        /**
         * 传入脚本时临时覆盖工具 config 后再分发
         */
        @Test
        void shouldInjectScriptCodeIntoConfig() {
            ToolEntity entity = activeTool("t1");
            entity.setConfig("return 1;");
            when(toolService.getById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(handler);
            when(handler.execute(any(), anyMap())).thenReturn(result("2"));

            service.customExecute("t1", "return 1 + 1;", Map.of());

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(handler).execute(captor.capture(), anyMap());
            assertThat(captor.getValue().getConfig()).isEqualTo("return 1 + 1;");
        }

        /**
         * 未传脚本时保留工具原有 config
         */
        @Test
        void shouldKeepOriginalConfigWhenScriptNull() {
            ToolEntity entity = activeTool("t1");
            entity.setConfig("return 42;");
            when(toolService.getById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(handler);
            when(handler.execute(any(), anyMap())).thenReturn(result("42"));

            service.customExecute("t1", null, Map.of());

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(handler).execute(captor.capture(), anyMap());
            assertThat(captor.getValue().getConfig()).isEqualTo("return 42;");
        }

        /**
         * 工具未激活时同样拒绝执行
         */
        @Test
        void shouldRejectInactiveTool() {
            ToolEntity entity = activeTool("t1");
            entity.setStatus(ToolConstants.STATUS_INACTIVE);
            when(toolService.getById("t1")).thenReturn(entity);

            assertThatThrownBy(() -> service.customExecute("t1", "return 1;", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具未激活: t1");
        }
    }

    @Nested
    @DisplayName("mcpExecute - MCP 工具执行")
    class McpExecute {

        /**
         * 正常分发到 MCP 处理器
         */
        @Test
        void shouldDispatchToMcpHandler() {
            ToolEntity entity = activeTool("t1");
            when(toolService.getById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.MCP)).thenReturn(handler);
            when(handler.execute(entity, Map.of())).thenReturn(result("mcp-ok"));

            assertThat(service.mcpExecute("t1", Map.of()).getOutput()).isEqualTo("mcp-ok");
        }

        /**
         * 没有 MCP 处理器时抛出业务异常
         */
        @Test
        void shouldRejectMissingHandler() {
            when(toolService.getById("t1")).thenReturn(activeTool("t1"));
            when(handlerRegistry.get(ToolConstants.ToolType.MCP)).thenReturn(null);

            assertThatThrownBy(() -> service.mcpExecute("t1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("不支持的工具类型: " + ToolConstants.ToolType.MCP);
        }
    }

    @Nested
    @DisplayName("pluginExecute - 插件工具执行")
    class PluginExecute {

        /**
         * 正常分发到插件处理器
         */
        @Test
        void shouldDispatchToPluginHandler() {
            ToolEntity entity = activeTool("t1");
            when(toolService.getById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.PLUGIN)).thenReturn(handler);
            when(handler.execute(entity, Map.of())).thenReturn(result("plugin-ok"));

            assertThat(service.pluginExecute("t1", Map.of()).getOutput()).isEqualTo("plugin-ok");
        }

        /**
         * 没有插件处理器时抛出业务异常
         */
        @Test
        void shouldRejectMissingHandler() {
            when(toolService.getById("t1")).thenReturn(activeTool("t1"));
            when(handlerRegistry.get(ToolConstants.ToolType.PLUGIN)).thenReturn(null);

            assertThatThrownBy(() -> service.pluginExecute("t1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("不支持的工具类型: " + ToolConstants.ToolType.PLUGIN);
        }
    }
}
