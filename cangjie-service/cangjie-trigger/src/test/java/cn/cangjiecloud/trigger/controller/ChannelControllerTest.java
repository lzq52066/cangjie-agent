package cn.cangjiecloud.trigger.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.trigger.api.dto.ChannelCreateDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelUpdateDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import cn.cangjiecloud.trigger.service.IChannelService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChannelController} 单元测试：管理端接口的参数透传、分页结果包装与
 * 全渠道消息审计的条件拼接。直接 new Controller，依赖全部 mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("渠道管理接口 - ChannelController")
class ChannelControllerTest {

    @Mock
    private IChannelService channelService;
    @Mock
    private IChannelMessageService channelMessageService;

    private ChannelController controller;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        // 全渠道消息审计接口会构造针对 ChannelMessageEntity 的 LambdaQueryWrapper
        assistant.setCurrentNamespace("cn.cangjiecloud.trigger.mapper.ChannelMessageMapper");
        TableInfoHelper.initTableInfo(assistant, ChannelMessageEntity.class);
    }

    @BeforeEach
    void setUp() {
        controller = new ChannelController(channelService, channelMessageService);
    }

    private static ChannelEntity channel(String id) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(id);
        channel.setName("渠道-" + id);
        return channel;
    }

    private static ChannelMessageEntity message(String id) {
        ChannelMessageEntity message = new ChannelMessageEntity();
        message.setId(id);
        message.setChannelId("c1");
        return message;
    }

    // ------------------------------------------------------------------
    // CRUD 透传
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("CRUD 接口")
    class Crud {

        @Test
        @DisplayName("create 透传 DTO 并包装为成功响应")
        void createShouldDelegateAndWrap() {
            ChannelCreateDTO dto = new ChannelCreateDTO();
            dto.setName("公众号");
            dto.setType("wechat_mp");
            ChannelEntity created = channel("c1");
            when(channelService.create(dto)).thenReturn(created);

            R<ChannelEntity> response = controller.create(dto);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(created);
            verify(channelService).create(dto);
        }

        @Test
        @DisplayName("update 透传 id 与 DTO")
        void updateShouldDelegate() {
            ChannelUpdateDTO dto = new ChannelUpdateDTO();
            dto.setName("新名称");
            ChannelEntity updated = channel("c1");
            when(channelService.update("c1", dto)).thenReturn(updated);

            R<ChannelEntity> response = controller.update("c1", dto);

            assertThat(response.getData()).isSameAs(updated);
            verify(channelService).update("c1", dto);
        }

        @Test
        @DisplayName("delete 无返回数据，仅透传 id")
        void deleteShouldDelegate() {
            R<Void> response = controller.delete("c1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(channelService).delete("c1");
        }

        @Test
        @DisplayName("get 透传 id 并返回渠道详情")
        void getShouldDelegate() {
            ChannelEntity entity = channel("c1");
            when(channelService.getById("c1")).thenReturn(entity);

            R<ChannelEntity> response = controller.get("c1");

            assertThat(response.getData()).isSameAs(entity);
            verify(channelService).getById("c1");
        }

        @Test
        @DisplayName("enable / disable 透传 id 并返回更新后的渠道")
        void enableAndDisableShouldDelegate() {
            ChannelEntity enabled = channel("c1");
            enabled.setStatus("active");
            ChannelEntity disabled = channel("c1");
            disabled.setStatus("inactive");
            when(channelService.enable("c1")).thenReturn(enabled);
            when(channelService.disable("c1")).thenReturn(disabled);

            assertThat(controller.enable("c1").getData().getStatus()).isEqualTo("active");
            assertThat(controller.disable("c1").getData().getStatus()).isEqualTo("inactive");

            verify(channelService).enable("c1");
            verify(channelService).disable("c1");
        }

        @Test
        @DisplayName("list 透传过滤与分页参数并包装为 PageResult")
        void listShouldDelegateAndWrapPage() {
            Page<ChannelEntity> page = new Page<>(2, 5);
            page.setRecords(List.of(channel("c1"), channel("c2")));
            page.setTotal(7);
            when(channelService.pageQuery("公众", "wechat_mp", 2, 5)).thenReturn(page);

            R<PageResult<ChannelEntity>> response = controller.list("公众", "wechat_mp", 2, 5);

            assertThat(response.isSuccess()).isTrue();
            PageResult<ChannelEntity> data = response.getData();
            assertThat(data.getList()).hasSize(2);
            assertThat(data.getTotal()).isEqualTo(7);
            assertThat(data.getPage()).isEqualTo(2);
            assertThat(data.getSize()).isEqualTo(5);
            verify(channelService).pageQuery("公众", "wechat_mp", 2, 5);
        }

        @Test
        @DisplayName("messages 透传渠道 id 与分页参数")
        void messagesShouldDelegateToPageQuery() {
            Page<ChannelMessageEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(message("m1")));
            page.setTotal(1);
            when(channelMessageService.pageQuery("c1", 1, 10)).thenReturn(page);

            R<PageResult<ChannelMessageEntity>> response = controller.messages("c1", 1, 10);

            assertThat(response.getData().getList()).hasSize(1);
            assertThat(response.getData().getTotal()).isEqualTo(1);
            verify(channelMessageService).pageQuery("c1", 1, 10);
        }
    }

    // ------------------------------------------------------------------
    // allMessages 审计查询
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("allMessages 全渠道消息审计")
    class AllMessages {

        private final IPage<ChannelMessageEntity> stub = new Page<>(1, 10);

        @Test
        @DisplayName("全部过滤条件生效：渠道/类型/应用/用户/状态/关键词（内容与回复）")
        void shouldApplyAllFilters() {
            when(channelMessageService.page(any(IPage.class), any(Wrapper.class))).thenReturn(stub);

            R<PageResult<ChannelMessageEntity>> response = controller.allMessages(
                    "c1", "wechat", "app1", "open-1", "processed", "hello", 1, 10);

            assertThat(response.getData()).isNotNull();

            ArgumentCaptor<IPage<ChannelMessageEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<ChannelMessageEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(channelMessageService).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql())
                    .contains("channel_id")
                    .contains("channel_type")
                    .contains("application_id")
                    .contains("open_id")
                    .contains("status")
                    .contains("content")
                    .contains("reply_content");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("c1", "wechat", "app1", "open-1", "processed", "%hello%");
        }

        @Test
        @DisplayName("过滤条件为 null 或空白时全部跳过，仅保留排序")
        void shouldSkipBlankFilters() {
            when(channelMessageService.page(any(IPage.class), any(Wrapper.class))).thenReturn(stub);

            controller.allMessages(null, " ", null, "", "  ", null, 1, 10);

            ArgumentCaptor<Wrapper<ChannelMessageEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(channelMessageService).page(any(IPage.class), wrapperCap.capture());

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getParamNameValuePairs()).isEmpty();
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }
    }
}
