package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.dto.ProblemCreateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeProblemEntity;
import cn.cangjiecloud.knowledge.entity.ProblemParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeProblemMapper;
import cn.cangjiecloud.knowledge.mapper.ProblemParagraphMapper;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IProblemParagraphService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeProblemServiceImpl} 单元测试。
 * <p>
 * 覆盖问题创建/更新/删除、段落关联（含空值与全量替换）、分页查询与问题路召回；
 * spy 拦截 MyBatis-Plus {@code ServiceImpl} 继承方法，不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeProblemServiceImplTest {

    @Mock
    private IKnowledgeBaseService knowledgeBaseService;
    @Mock
    private IProblemParagraphService problemParagraphService;

    private KnowledgeProblemServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(KnowledgeProblemMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, KnowledgeProblemEntity.class);
        MapperBuilderAssistant relationAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        relationAssistant.setCurrentNamespace(ProblemParagraphMapper.class.getName());
        TableInfoHelper.initTableInfo(relationAssistant, ProblemParagraphEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new KnowledgeProblemServiceImpl(knowledgeBaseService, problemParagraphService));
    }

    // ---------- 测试数据构造 ----------

    private static KnowledgeProblemEntity problem(String id, String content) {
        KnowledgeProblemEntity problem = new KnowledgeProblemEntity();
        problem.setId(id);
        problem.setKnowledgeBaseId("kb1");
        problem.setContent(content);
        problem.setSource("manual");
        return problem;
    }

    private static ProblemParagraphEntity relation(String problemId, String paragraphId) {
        ProblemParagraphEntity relation = new ProblemParagraphEntity();
        relation.setProblemId(problemId);
        relation.setParagraphId(paragraphId);
        relation.setKnowledgeBaseId("kb1");
        return relation;
    }

    private static ProblemCreateDTO dto(String content, List<String> paragraphIds) {
        ProblemCreateDTO dto = new ProblemCreateDTO();
        dto.setContent(content);
        dto.setParagraphIds(paragraphIds);
        return dto;
    }

    private void stubSaveAssignId(String id) {
        doAnswer(invocation -> {
            ((KnowledgeProblemEntity) invocation.getArgument(0)).setId(id);
            return true;
        }).when(service).save(any());
    }

    // ---------- create ----------

    @Nested
    class Create {

        @Test
        void shouldPersistProblemAndAssociateParagraphs() {
            // 正常场景：内容去空格、来源 manual，关联段落批量落库（空串被过滤）
            stubSaveAssignId("prob1");
            when(problemParagraphService.saveBatch(anyList())).thenReturn(true);

            KnowledgeProblemEntity created = service.create("kb1",
                    dto("  问题内容  ", List.of("pa1", " ", "pa2")));

            verify(knowledgeBaseService).checkAccess("kb1");
            assertThat(created.getContent()).isEqualTo("问题内容");
            assertThat(created.getSource()).isEqualTo("manual");
            assertThat(created.getKnowledgeBaseId()).isEqualTo("kb1");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ProblemParagraphEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(problemParagraphService).saveBatch(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue()).allSatisfy(r -> {
                assertThat(r.getProblemId()).isEqualTo("prob1");
                assertThat(r.getKnowledgeBaseId()).isEqualTo("kb1");
            });
            assertThat(captor.getValue()).extracting(ProblemParagraphEntity::getParagraphId)
                    .containsExactly("pa1", "pa2");
        }

        @Test
        void shouldSkipAssociationWhenParagraphIdsEmpty() {
            // 边界场景：未提供段落 ID 时不写关联表
            stubSaveAssignId("prob1");

            service.create("kb1", dto("问题", null));
            service.create("kb1", dto("问题", List.of()));

            verify(problemParagraphService, never()).saveBatch(anyList());
        }
    }

    // ---------- update / delete ----------

    @Nested
    class UpdateAndDelete {

        @Test
        void shouldThrowWhenProblemMissing() {
            // 异常场景：问题不存在
            doReturn(null).when(service).getById("missing");

            assertThatThrownBy(() -> service.update("missing", dto("新内容", null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("问题不存在");
            assertThatThrownBy(() -> service.delete("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("问题不存在");
        }

        @Test
        void shouldReplaceRelationsWhenParagraphIdsProvided() {
            // 正常场景：更新内容并全量替换关联段落
            doReturn(problem("prob1", "旧问题")).when(service).getById("prob1");
            doReturn(true).when(service).updateById(any());
            when(problemParagraphService.remove(any())).thenReturn(true);
            when(problemParagraphService.saveBatch(anyList())).thenReturn(true);

            KnowledgeProblemEntity updated = service.update("prob1",
                    dto(" 新问题 ", List.of("pa9")));

            verify(knowledgeBaseService).checkAccess("kb1");
            assertThat(updated.getContent()).isEqualTo("新问题");
            verify(problemParagraphService).remove(any());
            verify(problemParagraphService).saveBatch(anyList());
        }

        @Test
        void shouldKeepContentAndRelationsWhenFieldsBlankOrNull() {
            // 边界场景：内容为空白保持原值，paragraphIds 为 null 不动关联
            doReturn(problem("prob1", "旧问题")).when(service).getById("prob1");
            doReturn(true).when(service).updateById(any());

            KnowledgeProblemEntity updated = service.update("prob1", dto("   ", null));

            assertThat(updated.getContent()).isEqualTo("旧问题");
            verify(problemParagraphService, never()).remove(any());
            verify(problemParagraphService, never()).saveBatch(anyList());
        }

        @Test
        void shouldRemoveRelationsAndProblemOnDelete() {
            // 正常场景：删除问题同时清理关联
            doReturn(problem("prob1", "问题")).when(service).getById("prob1");
            doReturn(true).when(service).removeById(anyString());
            when(problemParagraphService.remove(any())).thenReturn(true);

            service.delete("prob1");

            verify(problemParagraphService).remove(any());
            verify(service).removeById("prob1");
        }

        @Test
        void shouldCheckAccessBeforeAssociating() {
            // 正常场景：补充关联前校验所属知识库权限
            doReturn(problem("prob1", "问题")).when(service).getById("prob1");
            when(problemParagraphService.saveBatch(anyList())).thenReturn(true);

            service.associate("prob1", List.of("pa1"));

            verify(knowledgeBaseService).checkAccess("kb1");
            verify(problemParagraphService).saveBatch(anyList());
        }
    }

    // ---------- pageQuery / pageQueryParagraphIds ----------

    @Nested
    class PageQueries {

        @Test
        void shouldPageProblemsWithDefaults() {
            // 边界场景：分页参数缺省 1/10
            Page<KnowledgeProblemEntity> page = new Page<>(1, 10);
            doReturn(page).when(service).page(any(), any());

            IPage<KnowledgeProblemEntity> result = service.pageQuery("kb1", null, null);

            assertThat(result).isSameAs(page);
            verify(knowledgeBaseService).checkAccess("kb1");
        }

        @Test
        void shouldConvertRelationPageToParagraphIds() {
            // 正常场景：关联分页转换为段落 ID 分页
            Page<ProblemParagraphEntity> relationPage = new Page<>(1, 10);
            relationPage.setRecords(List.of(relation("prob1", "pa1"), relation("prob1", "pa2")));
            relationPage.setTotal(2);
            when(problemParagraphService.page(any(), any())).thenReturn(relationPage);

            IPage<String> result = service.pageQueryParagraphIds("prob1", null, null);

            assertThat(result.getRecords()).containsExactly("pa1", "pa2");
            assertThat(result.getTotal()).isEqualTo(2);
        }
    }

    // ---------- recallParagraphs ----------

    @Nested
    class RecallParagraphs {

        @Test
        void shouldReturnEmptyForBlankInput() {
            // 边界场景：知识库列表为空或查询文本为空直接返回空 Map
            assertThat(service.recallParagraphs(null, "查询", 5)).isEmpty();
            assertThat(service.recallParagraphs(List.of(), "查询", 5)).isEmpty();
            assertThat(service.recallParagraphs(List.of("kb1"), "   ", 5)).isEmpty();

            verify(service, never()).list(any(Wrapper.class));
        }

        @Test
        void shouldReturnEmptyWhenNoProblemMatched() {
            // 边界场景：未匹配到问题时不查询关联表
            doReturn(List.of()).when(service).list(any(Wrapper.class));

            assertThat(service.recallParagraphs(List.of("kb1"), "查询", 5)).isEmpty();

            verify(problemParagraphService, never()).list(any(Wrapper.class));
        }

        @Test
        void shouldMapParagraphsToMatchedProblemContent() {
            // 正常场景：命中的问题段落映射为 段落ID -> 问题内容
            doReturn(List.of(problem("prob1", "什么是A"), problem("prob2", "什么是B")))
                    .when(service).list(any(Wrapper.class));
            when(problemParagraphService.list(any(Wrapper.class))).thenReturn(List.of(
                    relation("prob1", "pa1"),
                    relation("prob2", "pa2"),
                    relation("prob1", "pa3")));

            Map<String, String> recalled = service.recallParagraphs(List.of("kb1"), " 什么是 ", 10);

            assertThat(recalled).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "pa1", "什么是A", "pa2", "什么是B", "pa3", "什么是A"));
        }

        @Test
        void shouldRespectLimitAndDeduplicateParagraphs() {
            // 边界场景：超出 limit 截断，重复段落保留首次命中的问题
            doReturn(List.of(problem("prob1", "Q1"), problem("prob2", "Q2")))
                    .when(service).list(any(Wrapper.class));
            when(problemParagraphService.list(any(Wrapper.class))).thenReturn(List.of(
                    relation("prob1", "pa1"),
                    relation("prob2", "pa1"),
                    relation("prob2", "pa2"),
                    relation("prob1", "pa3")));

            Map<String, String> recalled = service.recallParagraphs(List.of("kb1"), "Q", 2);

            assertThat(recalled).hasSize(2);
            assertThat(recalled.get("pa1")).isEqualTo("Q1");
            assertThat(recalled.get("pa2")).isEqualTo("Q2");
        }
    }
}
