package cn.cangjiecloud.prompt.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.mapper.LongTermMemoryMapper;
import cn.cangjiecloud.prompt.memory.MemoryScorer;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryController} 单元测试。
 * <p>
 * 依赖的 {@link ILongTermMemoryService} 与 {@link MemoryScorer} 均为 mock；
 * {@link UserContext} 以静态 mock 替代 Sa-Token 上下文（未打桩时返回 null，代表无登录用户）。
 */
@ExtendWith(MockitoExtension.class)
class MemoryControllerTest {

    @Mock
    private ILongTermMemoryService longTermMemoryService;

    @Mock
    private MemoryScorer memoryScorer;

    private MemoryController controller;

    private MockedStatic<UserContext> userContextMock;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(LongTermMemoryMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, LongTermMemoryEntity.class);
    }

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        controller = new MemoryController(longTermMemoryService, memoryScorer);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    private static LongTermMemoryEntity memory(String id, String userId, Double confidence, Integer triggerCount) {
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setApplicationId("app1");
        entity.setDimension("preference");
        entity.setContent("记忆内容");
        entity.setConfidence(confidence);
        entity.setTriggerCount(triggerCount);
        entity.setIsActive(true);
        entity.setMemoryType("user");
        return entity;
    }

    private static UserIdentity identity(String role) {
        UserIdentity identity = new UserIdentity();
        identity.setRole(role);
        return identity;
    }

    private IPage<LongTermMemoryEntity> stubCandidates(List<LongTermMemoryEntity> records, long total) {
        IPage<LongTermMemoryEntity> page = new Page<>(1, 1000);
        page.setRecords(records);
        page.setTotal(total);
        when(longTermMemoryService.page(any(), any())).thenReturn(page);
        return page;
    }

    @SuppressWarnings("unchecked")
    private Wrapper<LongTermMemoryEntity> capturedQueryWrapper() {
        ArgumentCaptor<Wrapper<LongTermMemoryEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(longTermMemoryService).page(any(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("list：记忆列表")
    class ListMemories {

        @Test
        void 非管理员未传userId时强制查询本人记忆且默认仅激活() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");
            LongTermMemoryEntity weak = memory("m1", "u1", 0.5, 1);
            LongTermMemoryEntity strong = memory("m2", "u1", 0.9, 3);
            stubCandidates(List.of(weak, strong), 2);
            when(memoryScorer.score(weak)).thenReturn(0.2);
            when(memoryScorer.score(strong)).thenReturn(0.8);

            R<PageResult<Map<String, Object>>> result = controller.list(null, null, null, null, null, 1, 10);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).extracting(item -> item.get("memory"))
                    .containsExactly(strong, weak);
            assertThat(result.getData().getList().get(0)).containsEntry("score", 0.8);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) capturedQueryWrapper();
            assertThat(wrapper.getTargetSql())
                    .contains("user_id =")
                    .contains("is_active =");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("u1", true);
        }

        @Test
        void 非管理员且无登录用户时抛出异常() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));

            assertThatThrownBy(() -> controller.list(null, null, null, null, null, 1, 10))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("userId 不能为空");
            verify(longTermMemoryService, never()).page(any(), any());
        }

        @Test
        void 管理员按指定用户过滤() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));
            LongTermMemoryEntity entity = memory("m1", "u2", 0.9, 1);
            stubCandidates(List.of(entity), 1);
            when(memoryScorer.score(entity)).thenReturn(0.9);

            controller.list("u2", null, null, null, null, 1, 10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) capturedQueryWrapper();
            assertThat(wrapper.getTargetSql()).contains("user_id =");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("u2");
        }

        @Test
        void 管理员未传userId时查询全部用户且可包含停用记忆() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));
            stubCandidates(List.of(), 0);

            controller.list(null, "app1", "goal", "scene", Boolean.TRUE, 1, 10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) capturedQueryWrapper();
            assertThat(wrapper.getTargetSql())
                    .contains("application_id =")
                    .contains("dimension =")
                    .contains("memory_type =")
                    .doesNotContain("is_active");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("app1", "goal", "scene");
        }

        @Test
        void 候选集限定评分窗口并降序分页且总条数按窗口截断() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));
            LongTermMemoryEntity low = memory("m1", "u1", 0.1, 1);
            LongTermMemoryEntity high = memory("m2", "u1", 0.9, 5);
            LongTermMemoryEntity mid = memory("m3", "u1", 0.5, 2);
            stubCandidates(List.of(low, high, mid), 5000);
            when(memoryScorer.score(low)).thenReturn(0.1);
            when(memoryScorer.score(high)).thenReturn(0.9);
            when(memoryScorer.score(mid)).thenReturn(0.5);

            R<PageResult<Map<String, Object>>> result = controller.list("u1", null, null, null, null, 2, 1);

            ArgumentCaptor<IPage<LongTermMemoryEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            verify(longTermMemoryService).page(pageCaptor.capture(), any());
            assertThat(((Page<?>) pageCaptor.getValue()).getCurrent()).isEqualTo(1);
            assertThat(((Page<?>) pageCaptor.getValue()).getSize()).isEqualTo(1000);

            PageResult<Map<String, Object>> data = result.getData();
            assertThat(data.getList()).extracting(item -> item.get("memory")).containsExactly(mid);
            assertThat(data.getTotal()).isEqualTo(1000);
            assertThat(data.getPage()).isEqualTo(2);
            assertThat(data.getSize()).isEqualTo(1);
        }

        @Test
        void 评分四舍五入保留三位小数() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));
            LongTermMemoryEntity entity = memory("m1", "u1", 0.5, 1);
            stubCandidates(List.of(entity), 1);
            when(memoryScorer.score(entity)).thenReturn(0.12349);

            R<PageResult<Map<String, Object>>> result = controller.list(null, null, null, null, null, 1, 10);

            assertThat(result.getData().getList()).hasSize(1);
            Double score = (Double) result.getData().getList().get(0).get("score");
            assertThat(score).isCloseTo(0.123, within(1e-9));
        }
    }

    @Nested
    @DisplayName("create：手动录入记忆")
    class Create {

        @Test
        void userId或content为空时抛出异常() {
            LongTermMemoryEntity missingUser = memory("m1", null, null, null);
            LongTermMemoryEntity missingContent = memory("m2", "u1", null, null);
            missingContent.setContent("   ");

            assertThatThrownBy(() -> controller.create(missingUser))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("userId 与 content 不能为空");
            assertThatThrownBy(() -> controller.create(missingContent))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("userId 与 content 不能为空");
            verify(longTermMemoryService, never()).upsert(any());
        }

        @Test
        void 非管理员为其他用户录入时被拒绝() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            assertThatThrownBy(() -> controller.create(memory("m1", "u2", null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("无权操作其他用户的记忆");
            verify(longTermMemoryService, never()).upsert(any());
        }

        @Test
        void 补全默认字段后交给upsert() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");
            LongTermMemoryEntity entity = memory("m1", "u1", null, null);
            entity.setDimension("  ");
            entity.setApplicationId(null);
            entity.setMemoryType(null);
            when(longTermMemoryService.upsert(entity)).thenReturn(entity);

            R<LongTermMemoryEntity> result = controller.create(entity);

            assertThat(result.getData()).isSameAs(entity);
            assertThat(entity.getDimension()).isEqualTo("preference");
            assertThat(entity.getApplicationId()).isEqualTo("global");
            assertThat(entity.getMemoryType()).isEqualTo("user");
            assertThat(entity.getSource()).isEqualTo("explicit");
            assertThat(entity.getConfidence()).isEqualTo(1.0);
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getTriggerCount()).isZero();
            assertThat(entity.getLastTriggeredAt()).isNotNull();
        }

        @Test
        void 已指定字段时保留原值() {
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));
            LongTermMemoryEntity entity = memory("m1", "u1", 0.7, 3);
            entity.setDimension("goal");
            entity.setApplicationId("app9");
            entity.setMemoryType("scene");
            entity.setSource("import");
            when(longTermMemoryService.upsert(entity)).thenReturn(entity);

            controller.create(entity);

            assertThat(entity.getDimension()).isEqualTo("goal");
            assertThat(entity.getApplicationId()).isEqualTo("app9");
            assertThat(entity.getMemoryType()).isEqualTo("scene");
            assertThat(entity.getConfidence()).isEqualTo(0.7);
        }
    }

    @Nested
    @DisplayName("update：编辑记忆")
    class Update {

        @Test
        void 记忆不存在时抛出异常() {
            when(longTermMemoryService.getById("m404")).thenReturn(null);

            assertThatThrownBy(() -> controller.update("m404", memory("m404", "u1", null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("记忆不存在");
            verify(longTermMemoryService, never()).editMemory(any(), any(), any(), any());
        }

        @Test
        void 非所有者编辑时被拒绝() {
            when(longTermMemoryService.getById("m1")).thenReturn(memory("m1", "u2", 0.5, 1));
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            assertThatThrownBy(() -> controller.update("m1", memory("m1", "u1", null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("无权操作其他用户的记忆");
            verify(longTermMemoryService, never()).editMemory(any(), any(), any(), any());
        }

        @Test
        void 所有者编辑时透传参数并返回最新数据() {
            LongTermMemoryEntity existing = memory("m1", "u1", 0.5, 1);
            LongTermMemoryEntity refreshed = memory("m1", "u1", 0.9, 1);
            when(longTermMemoryService.getById("m1")).thenReturn(existing, refreshed);
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            LongTermMemoryEntity patch = memory("m1", "u1", 0.9, null);
            patch.setContent("新内容");
            patch.setDimension("goal");
            R<LongTermMemoryEntity> result = controller.update("m1", patch);

            assertThat(result.getData()).isSameAs(refreshed);
            verify(longTermMemoryService).editMemory("m1", "新内容", 0.9, "goal");
        }
    }

    @Nested
    @DisplayName("deactivate / reactivate / delete：启停与删除")
    class Operations {

        @Test
        void 所有者停用激活删除均放行() {
            when(longTermMemoryService.getById("m1")).thenReturn(memory("m1", "u1", 0.5, 1));
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            assertThat(controller.deactivate("m1").isSuccess()).isTrue();
            assertThat(controller.reactivate("m1").isSuccess()).isTrue();
            assertThat(controller.delete("m1").isSuccess()).isTrue();

            verify(longTermMemoryService).deactivate("m1");
            verify(longTermMemoryService).reactivate("m1");
            verify(longTermMemoryService).deleteMemory("m1");
        }

        @Test
        void 非所有者删除时被拒绝() {
            when(longTermMemoryService.getById("m1")).thenReturn(memory("m1", "u2", 0.5, 1));
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("user"));
            userContextMock.when(UserContext::getUserId).thenReturn("u1");

            assertThatThrownBy(() -> controller.delete("m1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("无权操作其他用户的记忆");
            verify(longTermMemoryService, never()).deleteMemory(any());
        }

        @Test
        void 场景记忆字段透传() {
            LongTermMemoryEntity scene = memory("m2", "u1", 0.5, 1);
            scene.setMemoryType("scene");
            scene.setSessionId("sess1");
            scene.setLastTriggeredAt(LocalDateTime.now());
            when(longTermMemoryService.getById("m2")).thenReturn(scene);
            userContextMock.when(UserContext::getIdentity).thenReturn(identity("ADMIN"));

            assertThat(controller.reactivate("m2").isSuccess()).isTrue();

            verify(longTermMemoryService).reactivate("m2");
        }
    }
}
