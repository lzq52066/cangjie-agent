package cn.cangjiecloud.prompt.memory;

import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link MemoryScorer} 单元测试。
 * <p>
 * 覆盖置信度、时近衰减、触发强度三个因子及最终 [0,1] 归一化，纯内存计算。
 */
class MemoryScorerTest {

    private MemoryScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new MemoryScorer();
        // 手动注入 @Value 字段（半衰期 30 天）
        ReflectionTestUtils.setField(scorer, "halfLifeDays", 30.0);
    }

    private static LongTermMemoryEntity memory(Double confidence, Integer triggerCount,
                                               LocalDateTime lastTriggeredAt) {
        LongTermMemoryEntity memory = new LongTermMemoryEntity();
        memory.setConfidence(confidence);
        memory.setTriggerCount(triggerCount);
        memory.setLastTriggeredAt(lastTriggeredAt);
        return memory;
    }

    @Nested
    @DisplayName("时近衰减")
    class Recency {

        @Test
        void 从未触发时衰减权重取中值零点五() {
            // confidence=1, triggerCount=0 -> boost 0.5
            double score = scorer.score(memory(1.0, 0, null));

            assertThat(score).isCloseTo(0.25, within(1e-9));
        }

        @Test
        void 刚触发过不衰减() {
            double score = scorer.score(memory(1.0, 0, LocalDateTime.now()));

            assertThat(score).isCloseTo(0.5, within(0.01));
        }

        @Test
        void 经过一个半衰期后强度衰减到约百分之三十七() {
            double score = scorer.score(memory(1.0, 0, LocalDateTime.now().minusDays(30)));

            assertThat(score).isCloseTo(0.5 * Math.exp(-1), within(0.01));
        }

        @Test
        void 未来触发时间不产生负衰减() {
            double score = scorer.score(memory(1.0, 0, LocalDateTime.now().plusDays(5)));

            assertThat(score).isCloseTo(0.5, within(0.01));
        }

        @Test
        void 半衰期配置为零时按一天兜底() {
            ReflectionTestUtils.setField(scorer, "halfLifeDays", 0.0);

            double score = scorer.score(memory(1.0, 0, LocalDateTime.now().minusDays(1)));

            assertThat(score).isCloseTo(0.5 * Math.exp(-1), within(0.01));
        }
    }

    @Nested
    @DisplayName("触发强度加成")
    class TriggerBoost {

        @Test
        void 未触发与触发次数为空等价() {
            assertThat(scorer.score(memory(1.0, null, LocalDateTime.now())))
                    .isEqualTo(scorer.score(memory(1.0, 0, LocalDateTime.now())));
        }

        @Test
        void 触发次数为负时按零处理() {
            assertThat(scorer.score(memory(1.0, -5, LocalDateTime.now())))
                    .isEqualTo(scorer.score(memory(1.0, 0, LocalDateTime.now())));
        }

        @Test
        void 触发九次时加成封顶为归一上限() {
            // 1 + log10(1 + 9) = 2 -> 归一到 1.0
            double score = scorer.score(memory(1.0, 9, LocalDateTime.now()));

            assertThat(score).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void 触发次数极大时评分仍不超过一() {
            double score = scorer.score(memory(1.0, 1_000_000, LocalDateTime.now()));

            assertThat(score).isLessThanOrEqualTo(1.0);
        }

        @Test
        void 触发次数增长带来强度单调提升() {
            LocalDateTime now = LocalDateTime.now();

            double none = scorer.score(memory(1.0, 0, now));
            double few = scorer.score(memory(1.0, 3, now));
            double many = scorer.score(memory(1.0, 9, now));

            assertThat(none).isLessThan(few);
            assertThat(few).isLessThan(many);
        }
    }

    @Nested
    @DisplayName("置信度与归一化")
    class ConfidenceAndClamp {

        @Test
        void 置信度为空时使用默认零点八() {
            double score = scorer.score(memory(null, 0, LocalDateTime.now()));

            // 0.8 * 1 * 0.5
            assertThat(score).isCloseTo(0.4, within(1e-9));
        }

        @Test
        void 置信度为零时评分为零() {
            assertThat(scorer.score(memory(0.0, 5, LocalDateTime.now()))).isZero();
        }

        @Test
        void 置信度为负时评分截断为零() {
            assertThat(scorer.score(memory(-1.0, 5, LocalDateTime.now()))).isZero();
        }

        @Test
        void 乘积累加超过一时截断为一() {
            double score = scorer.score(memory(5.0, 1_000, LocalDateTime.now()));

            assertThat(score).isEqualTo(1.0);
        }
    }
}
