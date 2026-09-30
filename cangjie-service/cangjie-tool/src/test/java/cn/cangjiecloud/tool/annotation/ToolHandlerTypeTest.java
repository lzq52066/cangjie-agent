package cn.cangjiecloud.tool.annotation;

import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.handler.CustomToolHandler;
import cn.cangjiecloud.tool.handler.HttpToolHandler;
import cn.cangjiecloud.tool.handler.McpToolHandler;
import cn.cangjiecloud.tool.handler.PluginToolHandler;
import cn.cangjiecloud.tool.handler.SkillToolHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolHandlerType} 注解契约测试：注解必须运行期可见、只能标注在类上，
 * 且 5 个内置处理器各自声明的 toolType 与常量保持一致（自动注册依赖该契约）。
 */
@DisplayName("注解 - ToolHandlerType")
class ToolHandlerTypeTest {

    @Test
    @DisplayName("注解保留策略为 RUNTIME，目标为 TYPE")
    void shouldBeRuntimeRetainedAndTypeTargeted() {
        Retention retention = ToolHandlerType.class.getAnnotation(Retention.class);
        Target target = ToolHandlerType.class.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.TYPE);
    }

    @Test
    @DisplayName("内置处理器声明的 toolType 与常量一致")
    void builtinHandlersShouldDeclareExpectedTypes() {
        assertThat(annotationValue(HttpToolHandler.class)).isEqualTo(ToolConstants.ToolType.HTTP);
        assertThat(annotationValue(CustomToolHandler.class)).isEqualTo(ToolConstants.ToolType.CUSTOM);
        assertThat(annotationValue(McpToolHandler.class)).isEqualTo(ToolConstants.ToolType.MCP);
        assertThat(annotationValue(PluginToolHandler.class)).isEqualTo(ToolConstants.ToolType.PLUGIN);
        assertThat(annotationValue(SkillToolHandler.class)).isEqualTo(ToolConstants.ToolType.SKILL);
    }

    private static String annotationValue(Class<?> handlerClass) {
        ToolHandlerType annotation = handlerClass.getAnnotation(ToolHandlerType.class);
        assertThat(annotation).as("%s 应标注 @ToolHandlerType", handlerClass.getSimpleName()).isNotNull();
        return annotation.value();
    }
}
