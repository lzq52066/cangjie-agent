package cn.cangjiecloud.prompt.memory;

import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryDecayJob} 单元测试。
 * <p>
 * 依赖的 Service 与评分器全部 mock，仅验证遗忘任务的分流决策（低分遗忘 / 容量淘汰 / 保护规则）。
 */
@ExtendWith(MockitoExtension.class)
class MemoryDecayJobTest {

    @Mock
    private ILongTermMemoryService longTermMemoryService;

    @Mock
    private MemoryScorer memoryScorer;

    private MemoryDecayJob job;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("cn.cangjiecloud.prompt.mapper.LongTermMemoryMapper");
        TableInfoHelper.initTableInfo(assistant, LongTermMemoryEntity.class);
    }

    @BeforeEach
    void setUp() {
        job = new MemoryDecayJob(longTermMemoryService, memoryScorer);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "decayThreshold", 0.25);
        ReflectionTestUtils.setField(job, "capacityPerUser", 100);
    }

    private static LongTermMemoryEntity memory(String id, String userId, String applicationId,
                                               String source) {
        LongTermMemoryEntity memory = new LongTermMemoryEntity();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setApplicationId(applicationId);
        memory.setSource(source);
        memory.setContent("内容-" + id);
        return memory;
    }

    @Nested
    @DisplayName("任务开关与异常")
    class Lifecycle {

        @Test
        void 任务关闭时不查询任何记忆() {
            ReflectionTestUtils.setField(job, "enabled", false);

            job.runDecay();

            verify(longTermMemoryService, never()).list(any(Wrapper.class));
            verify(longTermMemoryService, never()).deactivateBatch(any(), any());
        }

        @Test
        void 查询异常时任务吞掉异常不向上抛出() {
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenThrow(new RuntimeException("db down"));

            assertThatCode(() -> job.runDecay()).doesNotThrowAnyException();
            verify(longTermMemoryService, never()).deactivateBatch(any(), any());
        }

        @Test
        void 没有激活记忆时提前返回() {
            when(longTermMemoryService.list(any(Wrapper.class))).thenReturn(List.of());

            job.runDecay();

            verify(longTermMemoryService, never()).deactivateBatch(any(), any());
            verify(memoryScorer, never()).score(any());
        }
    }

    @Nested
    @DisplayName("低分遗忘")
    class LowScoreDecay {

        @Test
        void 低于阈值的推断记忆被停用且操作人记为系统() {
            LongTermMemoryEntity weak = memory("m1", "u1", "a1", "inferred");
            LongTermMemoryEntity strong = memory("m2", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class))).thenReturn(List.of(weak, strong));
            when(memoryScorer.score(weak)).thenReturn(0.1);
            when(memoryScorer.score(strong)).thenReturn(0.9);

            job.runDecay();

            verify(longTermMemoryService).deactivateBatch(List.of("m1"), "system");
            verify(longTermMemoryService).deactivateBatch(List.of(), "system");
            verify(longTermMemoryService, never()).deactivateBatch(List.of("m2"), "system");
        }

        @Test
        void 显式录入的记忆不参与低分遗忘() {
            LongTermMemoryEntity explicit = memory("m1", "u1", "a1", "explicit");
            when(longTermMemoryService.list(any(Wrapper.class))).thenReturn(List.of(explicit));

            job.runDecay();

            verify(memoryScorer, never()).score(explicit);
            // 低分遗忘与容量淘汰两个阶段都会各自调用一次（均为空集合）
            verify(longTermMemoryService, times(2)).deactivateBatch(List.of(), "system");
        }

        @Test
        void 边界分数等于阈值时保留记忆() {
            LongTermMemoryEntity boundary = memory("m1", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class))).thenReturn(List.of(boundary));
            when(memoryScorer.score(boundary)).thenReturn(0.25);

            job.runDecay();

            // 分数等于阈值时保留，两个阶段传入的待停用集合均为空
            verify(longTermMemoryService, times(2)).deactivateBatch(List.of(), "system");
        }
    }

    @Nested
    @DisplayName("容量淘汰")
    class CapacityEviction {

        @Test
        void 超出容量上限时淘汰评分最低的记忆() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.0);
            ReflectionTestUtils.setField(job, "capacityPerUser", 2);

            LongTermMemoryEntity best = memory("m1", "u1", "a1", "inferred");
            LongTermMemoryEntity middle = memory("m2", "u1", "a1", "inferred");
            LongTermMemoryEntity worst = memory("m3", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenReturn(List.of(best, middle, worst));
            when(memoryScorer.score(best)).thenReturn(0.9);
            when(memoryScorer.score(middle)).thenReturn(0.5);
            when(memoryScorer.score(worst)).thenReturn(0.4);

            job.runDecay();

            verify(longTermMemoryService).deactivateBatch(List.of("m3"), "system");
        }

        @Test
        void 容量为零表示不限制() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.0);
            ReflectionTestUtils.setField(job, "capacityPerUser", 0);

            LongTermMemoryEntity m1 = memory("m1", "u1", "a1", "inferred");
            LongTermMemoryEntity m2 = memory("m2", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class))).thenReturn(List.of(m1, m2));
            when(memoryScorer.score(m1)).thenReturn(0.9);
            when(memoryScorer.score(m2)).thenReturn(0.9);

            job.runDecay();

            verify(longTermMemoryService, never())
                    .deactivateBatch(eq(List.of("m1")), any());
            verify(longTermMemoryService, never())
                    .deactivateBatch(eq(List.of("m2")), any());
            verify(longTermMemoryService).deactivateBatch(List.of(), "system");
        }

        @Test
        void 显式记忆占用配额但不会被淘汰() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.0);
            ReflectionTestUtils.setField(job, "capacityPerUser", 2);

            LongTermMemoryEntity protectedLow = memory("m1", "u1", "a1", "explicit");
            LongTermMemoryEntity best = memory("m2", "u1", "a1", "inferred");
            LongTermMemoryEntity middle = memory("m3", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenReturn(List.of(protectedLow, best, middle));
            when(memoryScorer.score(best)).thenReturn(0.9);
            when(memoryScorer.score(middle)).thenReturn(0.5);

            job.runDecay();

            // 需要淘汰 1 条，仅推断类可淘汰，取其中评分最低者
            verify(longTermMemoryService).deactivateBatch(List.of("m3"), "system");
        }

        @Test
        void 无可淘汰记忆时不产生淘汰动作() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.0);
            ReflectionTestUtils.setField(job, "capacityPerUser", 1);

            LongTermMemoryEntity explicit1 = memory("m1", "u1", "a1", "explicit");
            LongTermMemoryEntity explicit2 = memory("m2", "u1", "a1", "explicit");
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenReturn(List.of(explicit1, explicit2));

            job.runDecay();

            verify(memoryScorer, never()).score(any());
            // 低分遗忘为空集一次，容量淘汰因无可淘汰对象亦为空集一次
            verify(longTermMemoryService, times(2)).deactivateBatch(List.of(), "system");
        }

        @Test
        void 不同用户与应用分组独立计算容量() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.0);
            ReflectionTestUtils.setField(job, "capacityPerUser", 1);

            LongTermMemoryEntity u1a = memory("m1", "u1", "a1", "inferred");
            LongTermMemoryEntity u1b = memory("m2", "u1", "a1", "inferred");
            LongTermMemoryEntity other = memory("m3", "u2", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenReturn(List.of(u1a, u1b, other));
            when(memoryScorer.score(u1a)).thenReturn(0.8);
            when(memoryScorer.score(u1b)).thenReturn(0.2);
            when(memoryScorer.score(other)).thenReturn(0.1);

            job.runDecay();

            // 仅 u1:a1 分组超限，u2:a1 只有一条不应被淘汰
            verify(longTermMemoryService).deactivateBatch(List.of("m2"), "system");
        }
    }

    @Nested
    @DisplayName("批量停用参数")
    class BatchArguments {

        @Test
        void 低分与容量淘汰各自以系统身份批量停用且可能重叠() {
            ReflectionTestUtils.setField(job, "decayThreshold", 0.3);
            ReflectionTestUtils.setField(job, "capacityPerUser", 1);

            LongTermMemoryEntity decayed = memory("m1", "u1", "a1", "inferred");
            LongTermMemoryEntity survived = memory("m2", "u1", "a1", "inferred");
            LongTermMemoryEntity eliminated = memory("m3", "u1", "a1", "inferred");
            when(longTermMemoryService.list(any(Wrapper.class)))
                    .thenReturn(List.of(decayed, survived, eliminated));
            when(memoryScorer.score(decayed)).thenReturn(0.1);
            when(memoryScorer.score(survived)).thenReturn(0.5);
            when(memoryScorer.score(eliminated)).thenReturn(0.4);

            job.runDecay();

            // 低分环节停用 m1；容量环节需要淘汰 2 条，取评分最低的 m1、m3（m1 被重复停用一次，幂等）
            verify(longTermMemoryService).deactivateBatch(List.of("m1"), "system");
            verify(longTermMemoryService).deactivateBatch(List.of("m1", "m3"), "system");
        }
    }
}
