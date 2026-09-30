package cn.cangjiecloud.prompt.service.impl;

import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;
import cn.cangjiecloud.prompt.mapper.LongTermMemoryMapper;
import cn.cangjiecloud.prompt.memory.MemoryDedupProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LongTermMemoryServiceImpl} 单元测试。
 * <p>
 * 以 mock 注入 baseMapper（并预置 entityClass 让 lambdaQuery/lambdaUpdate 可解析列名），
 * EmbeddingProvider 用 mock 替代真实向量服务；全程不连接数据库与网络。
 */
@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceImplTest {

    @Mock
    private LongTermMemoryMapper baseMapper;

    @Mock
    private EmbeddingProvider embeddingProvider;

    /** 已注册向量能力的容器 */
    @Mock
    private ObjectProvider<EmbeddingProvider> embeddingProviderProvider;

    /** 未注册向量能力的容器 */
    @Mock
    private ObjectProvider<EmbeddingProvider> emptyProviderProvider;

    private MemoryDedupProperties dedupProperties;

    private LongTermMemoryServiceImpl service;

    private MockedStatic<UserContext> userContextMock;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper/LambdaUpdateWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(LongTermMemoryMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, LongTermMemoryEntity.class);
    }

    @BeforeEach
    void setUp() {
        // Sa-Token 上下文在单测中不存在，静态 mock 后未打桩的调用返回 null
        userContextMock = mockStatic(UserContext.class);
        dedupProperties = new MemoryDedupProperties();
        when(embeddingProviderProvider.getIfAvailable()).thenReturn(embeddingProvider);
        service = newService(embeddingProviderProvider);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    private LongTermMemoryServiceImpl newService(ObjectProvider<EmbeddingProvider> providerProvider) {
        LongTermMemoryServiceImpl real = new LongTermMemoryServiceImpl(dedupProperties, providerProvider);
        ReflectionTestUtils.setField(real, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(real, "entityClass", LongTermMemoryEntity.class);
        return spy(real);
    }

    private static LongTermMemoryEntity memory(String id, String content) {
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setId(id);
        entity.setUserId("u1");
        entity.setApplicationId("app1");
        entity.setDimension("preference");
        entity.setContent(content);
        entity.setConfidence(0.5);
        entity.setSource("inferred");
        entity.setTriggerCount(1);
        entity.setIsActive(true);
        entity.setMemoryType("user");
        entity.setSessionId("sess1");
        return entity;
    }

    private static MemorySimilarity similarity(String id, Double score) {
        MemorySimilarity similarity = new MemorySimilarity();
        similarity.setId(id);
        similarity.setContent("邻居内容");
        similarity.setSimilarity(score);
        return similarity;
    }

    @Nested
    @DisplayName("embed：生成向量")
    class Embed {

        @Test
        void 未注册向量能力时返回null且不抛异常() {
            LongTermMemoryServiceImpl degraded = new LongTermMemoryServiceImpl(dedupProperties, emptyProviderProvider);

            assertThat(degraded.embed("任意内容")).isNull();
        }

        @Test
        void 内容为空时返回null且不调用提供者() {
            assertThat(service.embed(null)).isNull();
            assertThat(service.embed("   ")).isNull();
            verifyNoInteractions(embeddingProvider);
        }

        @Test
        void 正常内容委托给提供者() {
            float[] vector = {1.0f, 0.5f};
            when(embeddingProvider.embed("偏好深色主题")).thenReturn(vector);

            assertThat(service.embed("偏好深色主题")).isSameAs(vector);
        }

        @Test
        void 提供者抛异常时降级返回null() {
            when(embeddingProvider.embed(anyString())).thenThrow(new IllegalStateException("向量服务不可用"));

            assertThat(service.embed("内容")).isNull();
        }
    }

    @Nested
    @DisplayName("findSimilar：向量近邻检索")
    class FindSimilar {

        @Test
        void 向量或探针为空时返回空列表且不查库() {
            LongTermMemoryEntity probe = memory("m1", "内容");

            assertThat(service.findSimilar(null, probe, 5)).isEmpty();
            assertThat(service.findSimilar(new float[0], probe, 5)).isEmpty();
            assertThat(service.findSimilar(new float[]{1.0f}, null, 5)).isEmpty();
            verifyNoInteractions(baseMapper);
        }

        @Test
        void 记忆类型为空时按user类型检索并传递向量字面量() {
            LongTermMemoryEntity probe = memory("m1", "内容");
            probe.setMemoryType("   ");
            List<MemorySimilarity> expected = List.of(similarity("m9", 0.95));
            when(baseMapper.searchSimilar("[1.0,0.0]", 2, "u1", "app1", "user", "preference", "sess1", 3))
                    .thenReturn(expected);

            assertThat(service.findSimilar(new float[]{1.0f, 0.0f}, probe, 3)).isSameAs(expected);
        }

        @Test
        void limit非正时使用配置的候选条数() {
            LongTermMemoryEntity probe = memory("m1", "内容");
            dedupProperties.setCandidateLimit(7);
            when(baseMapper.searchSimilar(anyString(), anyInt(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), eq(7))).thenReturn(List.of());

            assertThat(service.findSimilar(new float[]{1.0f, 0.0f}, probe, 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("insertNew：新增记忆")
    class InsertNew {

        @Test
        void 关键字段为空时填充默认值() {
            LongTermMemoryEntity entity = memory("mNew", "内容");
            entity.setConfidence(null);
            entity.setSource(null);
            entity.setTriggerCount(null);
            entity.setIsActive(null);

            LongTermMemoryEntity result = service.insertNew(entity, null);

            assertThat(result).isSameAs(entity);
            assertThat(entity.getConfidence()).isEqualTo(0.8);
            assertThat(entity.getSource()).isEqualTo("inferred");
            assertThat(entity.getTriggerCount()).isZero();
            assertThat(entity.getIsActive()).isTrue();
            verify(baseMapper).insert(entity);
        }

        @Test
        void 已指定字段时保持原值() {
            LongTermMemoryEntity entity = memory("mNew", "内容");
            entity.setConfidence(0.95);
            entity.setSource("explicit");

            service.insertNew(entity, null);

            assertThat(entity.getConfidence()).isEqualTo(0.95);
            assertThat(entity.getSource()).isEqualTo("explicit");
        }

        @Test
        void 提供非空向量时保存后回写向量() {
            LongTermMemoryEntity entity = memory("mNew", "内容");

            service.insertNew(entity, new float[]{1.0f, 0.25f});

            verify(baseMapper).updateEmbedding("mNew", "[1.0,0.25]", 2);
        }

        @Test
        void 空向量不回写() {
            LongTermMemoryEntity entity = memory("mNew", "内容");

            service.insertNew(entity, new float[0]);

            verify(baseMapper, never()).updateEmbedding(any(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("reinforce：强化已有记忆")
    class Reinforce {

        @Test
        void 记忆不存在时返回null且不更新() {
            assertThat(service.reinforce("m404", "explicit")).isNull();

            verify(baseMapper, never()).updateById(any(LongTermMemoryEntity.class));
        }

        @Test
        void 置信度按步长累加且封顶1() {
            LongTermMemoryEntity entity = memory("m1", "内容");
            entity.setConfidence(0.95);
            when(baseMapper.selectById("m1")).thenReturn(entity);

            LongTermMemoryEntity result = service.reinforce("m1", "batch");

            assertThat(result).isSameAs(entity);
            assertThat(entity.getConfidence()).isEqualTo(1.0);
            assertThat(entity.getUpdateTime()).isNotNull();
            verify(baseMapper).updateById(entity);
        }

        @Test
        void 置信度为null时按默认0点8强化() {
            LongTermMemoryEntity entity = memory("m1", "内容");
            entity.setConfidence(null);
            when(baseMapper.selectById("m1")).thenReturn(entity);

            service.reinforce("m1", "batch");

            assertThat(entity.getConfidence()).isCloseTo(0.9, within(1e-9));
        }

        @Test
        void 来源为explicit时升级来源且非explicit不改变来源() {
            LongTermMemoryEntity upgrade = memory("m1", "内容");
            upgrade.setSource("inferred");
            LongTermMemoryEntity keep = memory("m2", "内容");
            keep.setSource("import");
            when(baseMapper.selectById("m1")).thenReturn(upgrade);
            when(baseMapper.selectById("m2")).thenReturn(keep);

            service.reinforce("m1", "explicit");
            service.reinforce("m2", "batch");

            assertThat(upgrade.getSource()).isEqualTo("explicit");
            assertThat(keep.getSource()).isEqualTo("import");
        }
    }

    @Nested
    @DisplayName("mergeInto：合并记忆")
    class MergeInto {

        @Test
        void 目标不存在时返回null且不写库() {
            assertThat(service.mergeInto("m404", "合并内容", 0.9, null)).isNull();

            verify(baseMapper, never()).updateById(any(LongTermMemoryEntity.class));
            verify(baseMapper, never()).updateEmbedding(any(), anyString(), anyInt());
        }

        @Test
        void 保留较高置信度并小幅强化且替换内容() {
            LongTermMemoryEntity target = memory("m1", "旧内容");
            target.setConfidence(0.5);
            when(baseMapper.selectById("m1")).thenReturn(target);

            LongTermMemoryEntity result = service.mergeInto("m1", "合并后内容", 0.85, null);

            assertThat(result).isSameAs(target);
            assertThat(target.getContent()).isEqualTo("合并后内容");
            assertThat(target.getConfidence()).isCloseTo(0.95, within(1e-9));
            assertThat(target.getUpdateTime()).isNotNull();
            verify(baseMapper).updateById(target);
            verify(baseMapper, never()).updateEmbedding(any(), anyString(), anyInt());
        }

        @Test
        void 提供向量时合并后回写向量() {
            LongTermMemoryEntity target = memory("m1", "旧内容");
            when(baseMapper.selectById("m1")).thenReturn(target);

            service.mergeInto("m1", "合并后内容", 0.9, new float[]{0.5f, 0.25f});

            verify(baseMapper).updateEmbedding("m1", "[0.5,0.25]", 2);
        }
    }

    @Nested
    @DisplayName("editMemory：编辑记忆")
    class EditMemory {

        @Test
        void 记忆不存在时静默返回() {
            service.editMemory("m404", "新内容", 0.9, "goal");

            verify(baseMapper, never()).updateById(any(LongTermMemoryEntity.class));
            verifyNoInteractions(embeddingProvider);
        }

        @Test
        void 内容变更时更新字段并重算向量() {
            LongTermMemoryEntity entity = memory("m1", "旧内容");
            when(baseMapper.selectById("m1")).thenReturn(entity);
            when(embeddingProvider.embed("新内容")).thenReturn(new float[]{0.5f, 0.25f});

            service.editMemory("m1", "新内容", 0.9, "goal");

            assertThat(entity.getContent()).isEqualTo("新内容");
            assertThat(entity.getConfidence()).isEqualTo(0.9);
            assertThat(entity.getDimension()).isEqualTo("goal");
            assertThat(entity.getUpdateTime()).isNotNull();
            verify(baseMapper).updateById(entity);
            verify(baseMapper).updateEmbedding("m1", "[0.5,0.25]", 2);
        }

        @Test
        void 内容相同或为空白时不重算向量() {
            LongTermMemoryEntity entity = memory("m1", "相同内容");
            when(baseMapper.selectById("m1")).thenReturn(entity);

            service.editMemory("m1", "相同内容", null, null);
            service.editMemory("m1", "   ", null, null);

            verify(baseMapper, times(2)).updateById(entity);
            verifyNoInteractions(embeddingProvider);
            verify(baseMapper, never()).updateEmbedding(any(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("upsert：写入去重")
    class Upsert {

        @Test
        void 精确命中时强化已有记忆且跳过向量检索() {
            LongTermMemoryEntity existing = memory("m1", "用户偏好深色主题");
            existing.setConfidence(0.6);
            LongTermMemoryEntity incoming = memory(null, "用户偏好深色主题");
            incoming.setSource("explicit");

            ArgumentCaptor<LambdaQueryWrapper<LongTermMemoryEntity>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            doReturn(existing).when(service).getOne(wrapperCaptor.capture());
            when(baseMapper.selectById("m1")).thenReturn(existing);

            LongTermMemoryEntity result = service.upsert(incoming);

            assertThat(result).isSameAs(existing);
            assertThat(existing.getConfidence()).isCloseTo(0.7, within(1e-9));
            assertThat(existing.getSource()).isEqualTo("explicit");
            verify(baseMapper).updateById(existing);
            verify(baseMapper, never()).insert(any(LongTermMemoryEntity.class));
            verifyNoInteractions(embeddingProvider);

            assertThat(wrapperCaptor.getValue().getTargetSql())
                    .contains("user_id =")
                    .contains("application_id =")
                    .contains("dimension =")
                    .contains("content =")
                    .contains("deleted =");
            assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                    .contains("u1", "app1", "preference", "用户偏好深色主题", 0);
        }

        @Test
        void 语义近邻相似度达到阈值时强化已有记忆() {
            LongTermMemoryEntity incoming = memory(null, "偏好深色主题");
            LongTermMemoryEntity neighbor = memory("m9", "喜欢深色主题");
            doReturn(null).when(service).getOne(any());
            when(embeddingProvider.embed("偏好深色主题")).thenReturn(new float[]{1.0f, 0.0f});
            when(baseMapper.searchSimilar("[1.0,0.0]", 2, "u1", "app1", "user", "preference", "sess1", 5))
                    .thenReturn(List.of(similarity("m9", 0.95)));
            when(baseMapper.selectById("m9")).thenReturn(neighbor);

            LongTermMemoryEntity result = service.upsert(incoming);

            assertThat(result).isSameAs(neighbor);
            assertThat(neighbor.getConfidence()).isCloseTo(0.6, within(1e-9));
            verify(baseMapper).updateById(neighbor);
            verify(baseMapper, never()).insert(any(LongTermMemoryEntity.class));
        }

        @Test
        void 相似度为null的近邻被跳过并在后续命中时强化() {
            LongTermMemoryEntity incoming = memory(null, "偏好深色主题");
            LongTermMemoryEntity neighbor = memory("m9", "喜欢深色主题");
            doReturn(null).when(service).getOne(any());
            when(embeddingProvider.embed(anyString())).thenReturn(new float[]{1.0f, 0.0f});
            when(baseMapper.searchSimilar(anyString(), anyInt(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(List.of(similarity("m1", null), similarity("m9", 0.93)));
            when(baseMapper.selectById("m9")).thenReturn(neighbor);

            LongTermMemoryEntity result = service.upsert(incoming);

            assertThat(result).isSameAs(neighbor);
            verify(baseMapper).updateById(neighbor);
            verify(baseMapper, never()).insert(any(LongTermMemoryEntity.class));
        }

        @Test
        void 相似度均低于阈值时新增并回写向量() {
            LongTermMemoryEntity incoming = memory("mNew", "偏好深色主题");
            doReturn(null).when(service).getOne(any());
            when(embeddingProvider.embed("偏好深色主题")).thenReturn(new float[]{1.0f, 0.0f});
            when(baseMapper.searchSimilar(anyString(), anyInt(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(List.of(similarity("m9", 0.5)));

            LongTermMemoryEntity result = service.upsert(incoming);

            assertThat(result).isSameAs(incoming);
            assertThat(incoming.getConfidence()).isEqualTo(0.5);
            verify(baseMapper).insert(incoming);
            verify(baseMapper).updateEmbedding("mNew", "[1.0,0.0]", 2);
            verify(baseMapper, never()).updateById(any(LongTermMemoryEntity.class));
        }

        @Test
        void 无向量能力时跳过语义去重直接新增() {
            LongTermMemoryEntity incoming = memory("mNew", "偏好深色主题");
            doReturn(null).when(service).getOne(any());
            // embeddingProvider 未打桩：embed 返回 null，降级为仅精确去重

            LongTermMemoryEntity result = service.upsert(incoming);

            assertThat(result).isSameAs(incoming);
            verify(baseMapper).insert(incoming);
            verify(baseMapper, never()).searchSimilar(anyString(), anyInt(), any(),
                    any(), any(), any(), any(), anyInt());
            verify(baseMapper, never()).updateEmbedding(any(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("incrementTrigger：累计触发")
    class IncrementTrigger {

        @Test
        void 记忆不存在时不执行更新() {
            service.incrementTrigger("m404");

            verify(baseMapper, never()).update(any(), any());
        }

        @Test
        void 触发次数加一并刷新最近触发时间() {
            LongTermMemoryEntity entity = memory("m1", "内容");
            entity.setTriggerCount(2);
            when(baseMapper.selectById("m1")).thenReturn(entity);

            service.incrementTrigger("m1");

            ArgumentCaptor<LambdaUpdateWrapper<LongTermMemoryEntity>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(baseMapper).update(isNull(), wrapperCaptor.capture());
            assertThat(wrapperCaptor.getValue().getSqlSet())
                    .contains("trigger_count")
                    .contains("last_triggered_at");
            assertThat(wrapperCaptor.getValue().getTargetSql()).contains("id =");
            assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(3);
        }

        @Test
        void 触发次数为null时按0起算() {
            LongTermMemoryEntity entity = memory("m1", "内容");
            entity.setTriggerCount(null);
            entity.setLastTriggeredAt(LocalDateTime.now().minusDays(10));
            when(baseMapper.selectById("m1")).thenReturn(entity);

            service.incrementTrigger("m1");

            ArgumentCaptor<LambdaUpdateWrapper<LongTermMemoryEntity>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(baseMapper).update(isNull(), wrapperCaptor.capture());
            assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(1);
        }
    }

    @Nested
    @DisplayName("findActive / findActiveAll：查询激活记忆")
    class FindActive {

        @Test
        void 按用户应用维度过滤且仅返回激活记忆() {
            List<LongTermMemoryEntity> memories = List.of(memory("m1", "a"), memory("m2", "b"));
            when(baseMapper.selectList(any())).thenReturn(memories);

            List<LongTermMemoryEntity> result = service.findActive("u1", "app1", "preference");

            assertThat(result).isSameAs(memories);
            LambdaQueryWrapper<?> wrapper = captureQueryWrapper();
            assertThat(wrapper.getTargetSql())
                    .contains("user_id =")
                    .contains("application_id =")
                    .contains("dimension =")
                    .contains("is_active =");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("u1", "app1", "preference", true);
        }

        @Test
        void 维度为空白时不按维度过滤() {
            when(baseMapper.selectList(any())).thenReturn(List.of());

            service.findActive("u1", "app1", "   ");

            LambdaQueryWrapper<?> wrapper = captureQueryWrapper();
            assertThat(wrapper.getTargetSql())
                    .contains("user_id =")
                    .contains("is_active =")
                    .doesNotContain("dimension");
        }

        @Test
        void 查询全部激活记忆时按维度升序() {
            when(baseMapper.selectList(any())).thenReturn(List.of(memory("m1", "a")));

            List<LongTermMemoryEntity> result = service.findActiveAll("u1", "app1");

            assertThat(result).hasSize(1);
            LambdaQueryWrapper<?> wrapper = captureQueryWrapper();
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY dimension ASC");
            assertThat(wrapper.getTargetSql()).contains("is_active =");
        }

        private LambdaQueryWrapper<?> captureQueryWrapper() {
            ArgumentCaptor<Wrapper<LongTermMemoryEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(baseMapper).selectList(captor.capture());
            return (LambdaQueryWrapper<?>) captor.getValue();
        }
    }

    @Nested
    @DisplayName("findSceneMemories：查询场景记忆")
    class FindSceneMemories {

        @Test
        void 会话ID为空时返回空列表且不查库() {
            assertThat(service.findSceneMemories(null)).isEmpty();
            assertThat(service.findSceneMemories("   ")).isEmpty();

            verifyNoInteractions(baseMapper);
        }

        @Test
        void 按会话与场景类型过滤并按最近触发时间倒序() {
            List<LongTermMemoryEntity> memories = List.of(memory("m1", "场景事实"));
            when(baseMapper.selectList(any())).thenReturn(memories);

            List<LongTermMemoryEntity> result = service.findSceneMemories("sess1");

            assertThat(result).isSameAs(memories);
            ArgumentCaptor<Wrapper<LongTermMemoryEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(baseMapper).selectList(captor.capture());
            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) captor.getValue();
            assertThat(wrapper.getTargetSql())
                    .contains("session_id =")
                    .contains("memory_type =")
                    .contains("is_active =");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY last_triggered_at DESC");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("sess1", "scene", true);
        }
    }

    @Nested
    @DisplayName("deactivate / reactivate / deactivateBatch：启停记忆")
    class Activation {

        @Test
        void 无登录上下文时停用操作人记为system() {
            service.deactivate("m1");

            LambdaUpdateWrapper<LongTermMemoryEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet())
                    .contains("is_active")
                    .contains("update_by")
                    .contains("update_time");
            assertThat(wrapper.getTargetSql()).contains("id =");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(false, "system");
        }

        @Test
        void 登录用户停用时记录当前用户为操作人() {
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            service.deactivate("m1");

            LambdaUpdateWrapper<LongTermMemoryEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getParamNameValuePairs().values()).contains(false, "u1");
        }

        @Test
        void 重新激活并记录当前操作人() {
            userContextMock.when(UserContext::getUserId).thenReturn("u2");

            service.reactivate("m1");

            LambdaUpdateWrapper<LongTermMemoryEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet()).contains("is_active");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(true, "u2");
        }

        @Test
        void 批量停用空集合时不执行更新() {
            service.deactivateBatch(null, "system");
            service.deactivateBatch(List.of(), "system");

            verify(baseMapper, never()).update(any(), any());
        }

        @Test
        void 批量停用多条并记录操作人() {
            service.deactivateBatch(List.of("m1", "m2"), "system");

            LambdaUpdateWrapper<LongTermMemoryEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet())
                    .contains("is_active")
                    .contains("update_by")
                    .contains("update_time");
            assertThat(wrapper.getTargetSql()).containsIgnoringCase("IN");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("m1", "m2", false, "system");
        }

        @Test
        void 批量停用操作人为空白时不写入update_by() {
            service.deactivateBatch(List.of("m1"), "   ");

            LambdaUpdateWrapper<LongTermMemoryEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet())
                    .contains("is_active")
                    .doesNotContain("update_by");
            // 先渲染 WHERE（IN 集合参数在渲染时才写入 paramNameValuePairs）
            assertThat(wrapper.getTargetSql()).containsIgnoringCase("IN");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("m1", false);
        }

        private LambdaUpdateWrapper<LongTermMemoryEntity> captureUpdateWrapper() {
            ArgumentCaptor<LambdaUpdateWrapper<LongTermMemoryEntity>> captor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(baseMapper).update(isNull(), captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("deleteMemory：删除记忆")
    class DeleteMemory {

        @Test
        void 按主键删除() {
            service.deleteMemory("m1");

            verify(baseMapper).deleteById("m1");
        }
    }
}
