package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.knowledge.rag.RetrievalToolService;
import cn.cangjiecloud.tool.service.IToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ToolSpecAssembler} 单元测试：覆盖应用工具 → 技能 → agentic 检索工具的
 * 装配顺序、ID 列表解析容错，以及静态的 OpenAI 定义转换方法。
 */
@ExtendWith(MockitoExtension.class)
class ToolSpecAssemblerTest {

    /** 工具服务桩 */
    @Mock
    private IToolService toolService;

    /** 检索工具服务桩 */
    @Mock
    private RetrievalToolService retrievalToolService;

    /** 被测试装配器 */
    private ToolSpecAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ToolSpecAssembler(toolService, retrievalToolService);
    }

    /**
     * 构造应用实体
     */
    private ApplicationEntity application(String toolIds, String skillIds,
                                          String kbIds, String ragMode) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setToolIds(toolIds);
        entity.setSkillIds(skillIds);
        entity.setKnowledgeBaseIds(kbIds);
        entity.setRagMode(ragMode);
        return entity;
    }

    @Nested
    class Assemble {

        /** 应用为 null 时返回空列表 */
        @Test
        void shouldReturnEmptyWhenApplicationNull() {
            assertThat(assembler.assemble(null)).isEmpty();
        }

        /** 无任何关联资源时返回空列表且不查服务 */
        @Test
        void shouldReturnEmptyWhenNoIdsConfigured() {
            List<ToolSpecification> specs = assembler.assemble(application(null, null, null, "simple"));

            assertThat(specs).isEmpty();
            verifyNoInteractions(toolService);
        }

        /** 按 应用工具 → 技能 → agentic 检索工具顺序装配 */
        @Test
        void shouldAssembleInFixedOrder() {
            ToolSpecification toolSpec = ToolSpecification.builder().name("http_tool").build();
            ToolSpecification skillSpec = ToolSpecification.builder().name("skill_tool").build();
            ToolSpecification retrievalSpec = ToolSpecification.builder()
                    .name(RetrievalToolService.TOOL_NAME).build();
            when(toolService.getToolSpecifications(List.of("t1"))).thenReturn(List.of(toolSpec));
            when(toolService.getSkillSpecifications(List.of("s1"))).thenReturn(List.of(skillSpec));
            when(retrievalToolService.buildSpec(ToolSpecAssembler.AGENTIC_RETRIEVAL_SPEC_ID))
                    .thenReturn(retrievalSpec);

            List<ToolSpecification> specs = assembler.assemble(
                    application("[\"t1\"]", "[\"s1\"]", "[\"kb1\"]", "agentic"));

            assertThat(specs).containsExactly(toolSpec, skillSpec, retrievalSpec);
        }

        /** agentic 模式但无知识库时不追加检索规格 */
        @Test
        void shouldNotAddRetrievalSpecWhenNoKb() {
            List<ToolSpecification> specs = assembler.assemble(
                    application(null, null, "[]", "agentic"));

            assertThat(specs).isEmpty();
            verifyNoInteractions(retrievalToolService);
        }

        /** 非 agentic 模式即使有知识库也不追加检索规格 */
        @Test
        void shouldNotAddRetrievalSpecInSimpleMode() {
            assembler.assemble(application(null, null, "[\"kb1\"]", "simple"));

            verifyNoInteractions(retrievalToolService);
        }
    }

    /** 知识库 ID：应用为 null 返回空列表，正常时解析 JSON 数组 */
    @Test
    void shouldResolveKnowledgeBaseIds() {
        assertThat(assembler.knowledgeBaseIds(null)).isEmpty();
        assertThat(assembler.knowledgeBaseIds(application(null, null, "[\"kb1\",\"kb2\"]", null)))
                .containsExactly("kb1", "kb2");
    }

    @Nested
    class ParseStringList {

        /** 空白输入返回空列表 */
        @Test
        void shouldReturnEmptyForBlank() {
            assertThat(ToolSpecAssembler.parseStringList(null)).isEmpty();
            assertThat(ToolSpecAssembler.parseStringList(" ")).isEmpty();
        }

        /** 合法数组正常解析，null 元素与空白元素被跳过 */
        @Test
        void shouldParseValidArrayAndSkipBlank() {
            assertThat(ToolSpecAssembler.parseStringList("[\"a\",null,\"\",\"b\"]"))
                    .containsExactly("a", "b");
        }

        /** 非法 JSON 容错返回空列表 */
        @Test
        void shouldReturnEmptyForInvalidJson() {
            assertThat(ToolSpecAssembler.parseStringList("bad")).isEmpty();
        }
    }

    @Nested
    class ToDefinitions {

        /** null/空输入返回空列表 */
        @Test
        void shouldReturnEmptyForNullOrEmpty() {
            assertThat(ToolSpecAssembler.toDefinitions(null)).isEmpty();
            assertThat(ToolSpecAssembler.toDefinitions(List.of())).isEmpty();
        }

        /** 转换结果为 OpenAI function 结构，参数缺失时给空对象兜底 */
        @Test
        @SuppressWarnings("unchecked")
        void shouldConvertToFunctionDefinitions() {
            ToolSpecification withParams = ToolSpecification.builder()
                    .name("a").description("da")
                    .parameters(Map.of("type", "object"))
                    .build();
            ToolSpecification noParams = ToolSpecification.builder().name("b").description("db").build();

            List<Map<String, Object>> definitions =
                    ToolSpecAssembler.toDefinitions(List.of(withParams, noParams));

            assertThat(definitions).hasSize(2);
            Map<String, Object> first = definitions.get(0);
            assertThat(first.get("type")).isEqualTo("function");
            Map<String, Object> function = (Map<String, Object>) first.get("function");
            assertThat(function.get("name")).isEqualTo("a");
            assertThat(function.get("parameters")).isEqualTo(Map.of("type", "object"));

            Map<String, Object> secondFunction =
                    (Map<String, Object>) definitions.get(1).get("function");
            assertThat(((Map<String, Object>) secondFunction.get("parameters")).get("type"))
                    .isEqualTo("object");
        }
    }
}
