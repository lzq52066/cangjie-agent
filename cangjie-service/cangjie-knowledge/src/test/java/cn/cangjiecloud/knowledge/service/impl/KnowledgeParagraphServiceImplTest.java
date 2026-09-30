package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeParagraphMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link KnowledgeParagraphServiceImpl} 单元测试。
 * <p>
 * spy 拦截 MyBatis-Plus {@code ServiceImpl} 的 list/page/remove，
 * 验证查询条件（documentId / knowledgeBaseId 参数）与分页缺省值，不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeParagraphServiceImplTest {

    private KnowledgeParagraphServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(KnowledgeParagraphMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, KnowledgeParagraphEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new KnowledgeParagraphServiceImpl());
    }

    private static KnowledgeParagraphEntity paragraph(String id) {
        KnowledgeParagraphEntity p = new KnowledgeParagraphEntity();
        p.setId(id);
        p.setDocumentId("d1");
        p.setKnowledgeBaseId("kb1");
        p.setChunkIndex(0);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<KnowledgeParagraphEntity>> wrapperCaptor() {
        return (ArgumentCaptor<LambdaQueryWrapper<KnowledgeParagraphEntity>>)
                (ArgumentCaptor<?>) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    @Nested
    class ListQueries {

        @Test
        void listByDocumentShouldDelegateAndFilterByDocumentId() {
            // 正常场景：按文档 ID 查询段落列表
            List<KnowledgeParagraphEntity> paragraphs = List.of(paragraph("p1"), paragraph("p2"));
            doReturn(paragraphs).when(service).list(any(Wrapper.class));

            List<KnowledgeParagraphEntity> result = service.listByDocument("d1");

            assertThat(result).isSameAs(paragraphs);
            ArgumentCaptor<LambdaQueryWrapper<KnowledgeParagraphEntity>> captor = wrapperCaptor();
            verify(service).list(captor.capture());
            LambdaQueryWrapper<KnowledgeParagraphEntity> wrapper = captor.getValue();
            // MP 参数值对在生成 SQL 片段时才惰性填充，先触发 getTargetSql
            assertThat(wrapper.getTargetSql()).contains("document_id");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("d1");
        }

        @Test
        void listByKnowledgeBaseShouldDelegateAndFilterByBaseId() {
            // 正常场景：按知识库 ID 查询段落列表
            List<KnowledgeParagraphEntity> paragraphs = List.of(paragraph("p1"));
            doReturn(paragraphs).when(service).list(any(Wrapper.class));

            List<KnowledgeParagraphEntity> result = service.listByKnowledgeBase("kb1");

            assertThat(result).isSameAs(paragraphs);
            ArgumentCaptor<LambdaQueryWrapper<KnowledgeParagraphEntity>> captor = wrapperCaptor();
            verify(service).list(captor.capture());
            LambdaQueryWrapper<KnowledgeParagraphEntity> wrapper = captor.getValue();
            // MP 参数值对在生成 SQL 片段时才惰性填充，先触发 getTargetSql
            assertThat(wrapper.getTargetSql()).contains("knowledge_base_id");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("kb1");
        }
    }

    @Nested
    class PageQuery {

        @Test
        void shouldApplyPagingDefaults() {
            // 边界场景：分页参数缺省 1/10
            Page<KnowledgeParagraphEntity> page = new Page<>(1, 10);
            doReturn(page).when(service).page(any(), any());

            IPage<KnowledgeParagraphEntity> result = service.pageQuery("d1", null, null);

            assertThat(result).isSameAs(page);
        }

        @Test
        void shouldUseProvidedPagingParameters() {
            // 正常场景：显式分页参数透传
            Page<KnowledgeParagraphEntity> page = new Page<>(3, 20);
            doReturn(page).when(service).page(any(), any());

            assertThat(service.pageQuery("d1", 3, 20)).isSameAs(page);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<KnowledgeParagraphEntity>> captor =
                    ArgumentCaptor.forClass(Page.class);
            verify(service).page(captor.capture(), any());
            assertThat(captor.getValue().getCurrent()).isEqualTo(3);
            assertThat(captor.getValue().getSize()).isEqualTo(20);
        }
    }

    @Nested
    class Delete {

        @Test
        void deleteByDocumentShouldFilterByDocumentId() {
            // 正常场景：按文档 ID 删除全部段落
            doReturn(true).when(service).remove(any());

            service.deleteByDocument("d1");

            ArgumentCaptor<LambdaQueryWrapper<KnowledgeParagraphEntity>> captor = wrapperCaptor();
            verify(service).remove(captor.capture());
            LambdaQueryWrapper<KnowledgeParagraphEntity> wrapper = captor.getValue();
            // MP 参数值对在生成 SQL 片段时才惰性填充，先触发 getTargetSql
            assertThat(wrapper.getTargetSql()).contains("document_id");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("d1");
        }
    }
}
