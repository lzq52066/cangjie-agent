package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.knowledge.entity.ProblemParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.ProblemParagraphMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProblemParagraphServiceImpl} 单元测试。
 * <p>
 * 该类无自定义逻辑，仅验证 baseMapper 注入后，继承自 MyBatis-Plus {@code ServiceImpl}
 * 的 save/getById/removeById 正确委托给 {@link ProblemParagraphMapper}。
 */
@ExtendWith(MockitoExtension.class)
class ProblemParagraphServiceImplTest {

    @Mock
    private ProblemParagraphMapper problemParagraphMapper;

    private ProblemParagraphServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // ServiceImpl.removeById 需要读取实体 TableInfo（逻辑删除/填充判断），初始化缓存避免 NPE
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProblemParagraphMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProblemParagraphEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProblemParagraphServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", problemParagraphMapper);
    }

    private static ProblemParagraphEntity relation(String id) {
        ProblemParagraphEntity relation = new ProblemParagraphEntity();
        relation.setId(id);
        relation.setProblemId("prob1");
        relation.setParagraphId("para1");
        relation.setKnowledgeBaseId("kb1");
        return relation;
    }

    @Test
    void saveShouldDelegateToMapper() {
        // 正常场景：save 委托 mapper.insert
        ProblemParagraphEntity relation = relation("r1");
        when(problemParagraphMapper.insert(relation)).thenReturn(1);

        assertThat(service.save(relation)).isTrue();

        verify(problemParagraphMapper).insert(relation);
    }

    @Test
    void getByIdShouldDelegateToMapper() {
        // 正常场景：getById 委托 mapper.selectById
        ProblemParagraphEntity relation = relation("r1");
        when(problemParagraphMapper.selectById("r1")).thenReturn(relation);

        assertThat(service.getById("r1")).isSameAs(relation);
    }

    @Test
    void removeByIdShouldDelegateToMapper() {
        // 正常场景：removeById 委托 mapper.deleteById
        when(problemParagraphMapper.deleteById(any(Serializable.class))).thenReturn(1);

        assertThat(service.removeById("r1")).isTrue();

        verify(problemParagraphMapper).deleteById((Serializable) "r1");
    }
}
