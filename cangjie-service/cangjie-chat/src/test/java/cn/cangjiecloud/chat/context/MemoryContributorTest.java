package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.memory.MemoryScorer;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryContributor} 单元测试：按维度拼装用户记忆与场景记忆，空场景返回空片段。
 */
class MemoryContributorTest {

    private ILongTermMemoryService longTermMemoryService;
    private MemoryScorer memoryScorer;
    private MemoryContributor contributor;

    @BeforeEach
    void setUp() {
        longTermMemoryService = mock(ILongTermMemoryService.class);
        memoryScorer = mock(MemoryScorer.class);
        contributor = new MemoryContributor(longTermMemoryService, memoryScorer);
        ReflectionTestUtils.setField(contributor, "memoryInjectMaxCount", 20);
        ReflectionTestUtils.setField(contributor, "memoryInjectMaxChars", 2000);
        when(memoryScorer.score(org.mockito.ArgumentMatchers.any())).thenReturn(0.9);
    }

    private static LongTermMemoryEntity memory(String id, String dimension, String type, String content) {
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setId(id);
        entity.setDimension(dimension);
        entity.setMemoryType(type);
        entity.setContent(content);
        return entity;
    }

    private ContextRequest enabledRequest() {
        return ContextRequest.builder()
                .memoryEnabled(true).userId("user-1").applicationId("app-1").sessionId("sess-1")
                .attributes(new HashMap<>())
                .build();
    }

    @Test
    void metadataShouldDeclareMemorySlot() {
        assertThat(contributor.name()).isEqualTo("longTermMemory");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.MEMORY);
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportWhenEnabledWithUserAndApp() {
            assertThat(contributor.supports(enabledRequest())).isTrue();
        }

        @Test
        void shouldNotSupportWhenMemoryDisabled() {
            ContextRequest request = ContextRequest.builder().userId("u").applicationId("a")
                    .attributes(new HashMap<>()).build();
            assertThat(contributor.supports(request)).isFalse();
        }

        @Test
        void shouldNotSupportWhenUserOrAppMissing() {
            ContextRequest request = ContextRequest.builder().memoryEnabled(true).userId("u")
                    .attributes(new HashMap<>()).build();
            assertThat(contributor.supports(request)).isFalse();
        }
    }

    @Nested
    class Contribute {

        @Test
        void shouldReturnEmptyWhenNoMemoriesAtAll() {
            when(longTermMemoryService.findActiveAll("user-1", "app-1")).thenReturn(List.of());
            when(longTermMemoryService.findSceneMemories("sess-1")).thenReturn(List.of());

            assertThat(contributor.contribute(enabledRequest()).isEmpty()).isTrue();
            verify(longTermMemoryService, never()).incrementTrigger(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void shouldGroupUserMemoriesByDimensionAndTriggerThem() {
            when(longTermMemoryService.findActiveAll("user-1", "app-1")).thenReturn(List.of(
                    memory("1", "preference", "user", "喜欢简洁回答"),
                    memory("2", "background", "user", "是一名工程师")));
            when(longTermMemoryService.findSceneMemories("sess-1")).thenReturn(List.of());

            ContextFragment fragment = contributor.contribute(enabledRequest());

            assertThat(fragment.isEmpty()).isFalse();
            String content = fragment.getMessages().get(0).getContent();
            assertThat(content).contains("【用户偏好】").contains("喜欢简洁回答")
                    .contains("【用户背景】").contains("是一名工程师");
            verify(longTermMemoryService).incrementTrigger("1");
            verify(longTermMemoryService).incrementTrigger("2");
        }

        @Test
        void shouldExcludeSceneTypeFromUserMemories() {
            when(longTermMemoryService.findActiveAll("user-1", "app-1")).thenReturn(List.of(
                    memory("1", "goal", "scene", "本次任务目标")));
            when(longTermMemoryService.findSceneMemories("sess-1")).thenReturn(List.of());

            ContextFragment fragment = contributor.contribute(enabledRequest());

            assertThat(fragment.isEmpty()).isTrue();
        }

        @Test
        void shouldAppendSceneMemoriesFromSession() {
            when(longTermMemoryService.findActiveAll("user-1", "app-1")).thenReturn(List.of());
            when(longTermMemoryService.findSceneMemories("sess-1")).thenReturn(List.of(
                    memory("3", "goal", "scene", "本周完成周报模块")));

            ContextFragment fragment = contributor.contribute(enabledRequest());

            assertThat(fragment.getMessages().get(0).getContent())
                    .contains("【当前会话背景】").contains("本周完成周报模块");
            verify(longTermMemoryService).incrementTrigger("3");
        }
    }
}
