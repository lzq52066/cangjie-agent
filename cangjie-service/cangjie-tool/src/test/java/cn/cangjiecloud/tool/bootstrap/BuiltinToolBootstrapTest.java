package cn.cangjiecloud.tool.bootstrap;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BuiltinToolBootstrap 单元测试
 * <p>
 * 读取真实 classpath 下的 builtin-tools/tools.json（26 条内置工具定义），
 * 通过 mock 的 IToolService 拦截数据库写入，验证幂等导入逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("内置工具引导 - BuiltinToolBootstrap")
class BuiltinToolBootstrapTest {

    /** builtin-tools/tools.json 中的工具定义总数 */
    private static final int TOTAL_DEFINITIONS = 26;

    @Mock
    private IToolService toolService;

    private BuiltinToolBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new BuiltinToolBootstrap(toolService);
        ReflectionTestUtils.setField(bootstrap, "importBuiltin", true);
    }

    @Nested
    @DisplayName("run - 开关控制")
    class ImportSwitch {

        /**
         * importBuiltin=false 时不访问数据库
         */
        @Test
        void shouldSkipWhenDisabled() {
            ReflectionTestUtils.setField(bootstrap, "importBuiltin", false);

            bootstrap.run(null);

            verifyNoInteractions(toolService);
        }
    }

    @Nested
    @DisplayName("run - 新增导入")
    class InsertPath {

        /**
         * 全部定义均不存在时应逐条新增，且字段按类型正确构建
         */
        @Test
        void shouldInsertAllDefinitions() {
            bootstrap.run(null);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService, times(TOTAL_DEFINITIONS)).save(captor.capture());
            verify(toolService, never()).updateById(any());

            var saved = captor.getAllValues();
            assertThat(saved).hasSize(TOTAL_DEFINITIONS);
            assertThat(saved).allSatisfy(entity -> {
                assertThat(entity.getId()).isNotBlank();
                assertThat(entity.getStatus()).isEqualTo(ToolConstants.STATUS_ACTIVE);
                assertThat(entity.getToolType()).isNotBlank();
                // type 与 toolType 双写
                assertThat(entity.getType()).isEqualTo(entity.getToolType());
                // 未显式声明 functionName 时回退为内置 ID
                assertThat(entity.getFunctionName()).isEqualTo(entity.getId());
            });

            Map<String, Long> typeCount = saved.stream()
                    .collect(java.util.stream.Collectors.groupingBy(ToolEntity::getToolType, java.util.stream.Collectors.counting()));
            assertThat(typeCount).containsEntry(ToolConstants.ToolType.CUSTOM, 4L)
                    .containsEntry(ToolConstants.ToolType.PLUGIN, 14L)
                    .containsEntry(ToolConstants.ToolType.HTTP, 3L)
                    .containsEntry(ToolConstants.ToolType.LOCAL, 5L);
        }

        /**
         * CUSTOM 定义应从脚本文件读取 config；PLUGIN 使用 implementation 且 config 为空；
         * LOCAL 无服务端 config
         */
        @Test
        void shouldBuildTypeSpecificFields() {
            bootstrap.run(null);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService, times(TOTAL_DEFINITIONS)).save(captor.capture());

            assertThat(captor.getAllValues())
                    .filteredOn(e -> ToolConstants.ToolType.CUSTOM.equals(e.getToolType()))
                    .allSatisfy(e -> {
                        assertThat(e.getConfig()).isNotBlank();
                        assertThat(e.getImplementation()).isNull();
                    });
            assertThat(captor.getAllValues())
                    .filteredOn(e -> ToolConstants.ToolType.PLUGIN.equals(e.getToolType()))
                    .allSatisfy(e -> {
                        assertThat(e.getImplementation()).isNotBlank();
                        assertThat(e.getConfig()).isNull();
                    });
            assertThat(captor.getAllValues())
                    .filteredOn(e -> ToolConstants.ToolType.HTTP.equals(e.getToolType()))
                    .allSatisfy(e -> assertThat(JsonUtils.parseMap(e.getConfig())).containsKey("url"));
            assertThat(captor.getAllValues())
                    .filteredOn(e -> ToolConstants.ToolType.LOCAL.equals(e.getToolType()))
                    .allSatisfy(e -> assertThat(e.getConfig()).isNull());
        }

        /**
         * 单条定义失败（getById 抛异常）不影响其它工具导入
         */
        @Test
        void shouldContinueWhenSingleDefinitionFails() {
            when(toolService.getById(anyString())).thenReturn(null);
            when(toolService.getById("builtin_hash")).thenThrow(new RuntimeException("db error"));

            bootstrap.run(null);

            verify(toolService, times(TOTAL_DEFINITIONS - 1)).save(any());
        }
    }

    @Nested
    @DisplayName("run - 幂等更新")
    class UpdatePath {

        /**
         * 已存在的工具应更新而非新增，且保留原有状态
         */
        @Test
        void shouldUpdateExistingAndKeepStatus() {
            ToolEntity existing = new ToolEntity();
            existing.setId("builtin_hash");
            existing.setStatus(ToolConstants.STATUS_INACTIVE);
            when(toolService.getById(anyString())).thenReturn(existing);

            bootstrap.run(null);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService, times(TOTAL_DEFINITIONS)).updateById(captor.capture());
            verify(toolService, never()).save(any());

            assertThat(captor.getAllValues()).allSatisfy(entity -> {
                assertThat(entity.getId()).isNotBlank();
                assertThat(entity.getStatus()).isEqualTo(ToolConstants.STATUS_INACTIVE);
            });
        }

        /**
         * 已存在但状态为空时回退为 active
         */
        @Test
        void shouldFallbackNullStatusToActive() {
            ToolEntity existing = new ToolEntity();
            when(toolService.getById(anyString())).thenReturn(existing);

            bootstrap.run(null);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService, times(TOTAL_DEFINITIONS)).updateById(captor.capture());

            assertThat(captor.getAllValues())
                    .allSatisfy(entity -> assertThat(entity.getStatus()).isEqualTo(ToolConstants.STATUS_ACTIVE));
        }
    }
}
