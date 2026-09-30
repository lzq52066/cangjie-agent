package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.trigger.api.dto.ChannelCreateDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelUpdateDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.service.IWorkflowService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ChannelServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截继承自 MyBatis-Plus {@code ServiceImpl} 的 {@code save/getById/updateById/removeById/list/page}
 * 等方法，不注入 SqlSession、不访问数据库；对话服务、工作流服务与应用服务均为 mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("渠道服务 - ChannelServiceImpl")
class ChannelServiceImplTest {

    @Mock
    private IChatService chatService;
    @Mock
    private IChannelMessageService channelMessageService;
    @Mock
    private IApplicationService applicationService;
    @Mock
    private IWorkflowService workflowService;

    private ChannelServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.trigger.mapper.ChannelMapper");
        TableInfoHelper.initTableInfo(assistant, ChannelEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ChannelServiceImpl(chatService, channelMessageService, applicationService, workflowService));
    }

    private static ChannelEntity channel(String id, String type, String status, String applicationId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(id);
        channel.setName("渠道-" + id);
        channel.setType(type);
        channel.setStatus(status);
        channel.setApplicationId(applicationId);
        return channel;
    }

    private static ChannelReplyDTO messageRequest(String channelId, String content) {
        ChannelReplyDTO dto = new ChannelReplyDTO();
        dto.setChannelId(channelId);
        dto.setOpenId("open-1");
        dto.setMsgType("text");
        dto.setMessage(content);
        return dto;
    }

    private ChannelMessageEntity captureSavedMessage() {
        ArgumentCaptor<ChannelMessageEntity> captor = ArgumentCaptor.forClass(ChannelMessageEntity.class);
        verify(channelMessageService).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("字段完整拷贝，状态为空时默认 inactive，调用次数归零")
        void shouldFillAllFieldsAndDefaultStatus() {
            doReturn(true).when(service).save(any(ChannelEntity.class));

            ChannelCreateDTO dto = new ChannelCreateDTO();
            dto.setName("企业公众号");
            dto.setType("wechat_mp");
            dto.setApplicationId("app1");
            dto.setAppId("wx-app-id");
            dto.setAppSecret("wx-secret");
            dto.setToken("verify-token");
            dto.setEncodingAesKey("aes-key");
            dto.setVerifyToken("platform-token");
            dto.setConfig("{\"k\":1}");
            dto.setStatus("   ");

            ChannelEntity created = service.create(dto);

            assertThat(created.getStatus()).isEqualTo("inactive");
            assertThat(created.getCallCount()).isZero();

            ArgumentCaptor<ChannelEntity> captor = ArgumentCaptor.forClass(ChannelEntity.class);
            verify(service).save(captor.capture());
            ChannelEntity saved = captor.getValue();
            assertThat(saved).isSameAs(created);
            assertThat(saved.getName()).isEqualTo("企业公众号");
            assertThat(saved.getType()).isEqualTo("wechat_mp");
            assertThat(saved.getApplicationId()).isEqualTo("app1");
            assertThat(saved.getAppId()).isEqualTo("wx-app-id");
            assertThat(saved.getAppSecret()).isEqualTo("wx-secret");
            assertThat(saved.getToken()).isEqualTo("verify-token");
            assertThat(saved.getEncodingAesKey()).isEqualTo("aes-key");
            assertThat(saved.getVerifyToken()).isEqualTo("platform-token");
            assertThat(saved.getConfig()).isEqualTo("{\"k\":1}");
        }

        @Test
        @DisplayName("显式传入的 status 不被覆盖")
        void shouldKeepExplicitStatus() {
            doReturn(true).when(service).save(any(ChannelEntity.class));
            ChannelCreateDTO dto = new ChannelCreateDTO();
            dto.setName("钉钉机器人");
            dto.setType("dingtalk");
            dto.setStatus("active");

            ChannelEntity created = service.create(dto);

            assertThat(created.getStatus()).isEqualTo("active");
        }
    }

    // ------------------------------------------------------------------
    // update / delete
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("update / delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("渠道不存在时更新抛 ApiException")
        void updateShouldThrowWhenChannelMissing() {
            doReturn(null).when(service).getById("nope");

            assertThatThrownBy(() -> service.update("nope", new ChannelUpdateDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道不存在");
        }

        @Test
        @DisplayName("仅非空字段被覆盖，其余字段保留原值")
        void updateShouldApplyOnlyNonBlankFields() {
            ChannelEntity existing = channel("c1", "wechat", "inactive", "app1");
            existing.setAppSecret("old-secret");
            existing.setToken("old-token");
            doReturn(existing).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelUpdateDTO dto = new ChannelUpdateDTO();
            dto.setName("  新名称  ");
            dto.setStatus("active");
            dto.setAppSecret("");

            ChannelEntity updated = service.update("c1", dto);

            assertThat(updated).isSameAs(existing);
            assertThat(updated.getName()).isEqualTo("  新名称  ");
            assertThat(updated.getStatus()).isEqualTo("active");
            // 未传或空白字段保持原值
            assertThat(updated.getType()).isEqualTo("wechat");
            assertThat(updated.getApplicationId()).isEqualTo("app1");
            assertThat(updated.getAppSecret()).isEqualTo("old-secret");
            assertThat(updated.getToken()).isEqualTo("old-token");
            verify(service).updateById(existing);
        }

        @Test
        @DisplayName("渠道不存在时删除为空操作")
        void deleteShouldSkipWhenChannelMissing() {
            doReturn(null).when(service).getById("gone");

            service.delete("gone");

            verify(service, never()).removeById(anyString());
        }

        @Test
        @DisplayName("渠道存在时按 ID 删除")
        void deleteShouldRemoveExistingChannel() {
            doReturn(channel("c1", "wechat", "active", "app1")).when(service).getById("c1");
            doReturn(true).when(service).removeById(anyString());

            service.delete("c1");

            verify(service).removeById("c1");
        }
    }

    // ------------------------------------------------------------------
    // pageQuery / getByType
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("pageQuery / getByType")
    class Query {

        @Test
        @DisplayName("分页参数为空时回退 1/10，空白过滤条件不参与查询")
        void pageQueryShouldUseDefaultsAndSkipBlankFilters() {
            IPage<ChannelEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<ChannelEntity> result = service.pageQuery("  ", null, null, null);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<ChannelEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<ChannelEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("name").doesNotContain("type");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }

        @Test
        @DisplayName("关键字与类型过滤条件正确拼接并透传分页参数")
        void pageQueryShouldApplyKeywordAndTypeFilters() {
            IPage<ChannelEntity> stub = new Page<>(2, 5);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery("公众号", "wechat_mp", 2, 5);

            ArgumentCaptor<IPage<ChannelEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<ChannelEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(2);
            assertThat(requested.getSize()).isEqualTo(5);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).contains("name").contains("type");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("%公众号%", "wechat_mp");
        }

        @Test
        @DisplayName("类型为空时直接返回空列表且不查询")
        void getByTypeShouldReturnEmptyWhenBlank() {
            assertThat(service.getByType(" ")).isEmpty();
            assertThat(service.getByType(null)).isEmpty();

            verify(service, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("按类型查询启用中的渠道并按创建时间倒序")
        void getByTypeShouldQueryActiveChannels() {
            List<ChannelEntity> channels = List.of(channel("c1", "wechat", "active", "app1"));
            doReturn(channels).when(service).list(any(Wrapper.class));

            assertThat(service.getByType("wechat")).isSameAs(channels);

            ArgumentCaptor<Wrapper<ChannelEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) captor.getValue();
            assertThat(wrapper.getTargetSql()).contains("type").contains("status");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("wechat", "active");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }
    }

    // ------------------------------------------------------------------
    // enable / disable
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("enable / disable")
    class EnableAndDisable {

        @Test
        @DisplayName("渠道不存在时启用抛 ApiException")
        void enableShouldThrowWhenMissing() {
            doReturn(null).when(service).getById("nope");

            assertThatThrownBy(() -> service.enable("nope"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道不存在");
        }

        @Test
        @DisplayName("启用后状态置为 active 并持久化")
        void enableShouldSetActiveStatus() {
            ChannelEntity existing = channel("c1", "wechat", "inactive", "app1");
            doReturn(existing).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelEntity enabled = service.enable("c1");

            assertThat(enabled.getStatus()).isEqualTo("active");
            verify(service).updateById(existing);
        }

        @Test
        @DisplayName("渠道不存在时停用抛 ApiException")
        void disableShouldThrowWhenMissing() {
            doReturn(null).when(service).getById("nope");

            assertThatThrownBy(() -> service.disable("nope"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道不存在");
        }

        @Test
        @DisplayName("停用后状态置为 inactive 并持久化")
        void disableShouldSetInactiveStatus() {
            ChannelEntity existing = channel("c1", "wechat", "active", "app1");
            doReturn(existing).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelEntity disabled = service.disable("c1");

            assertThat(disabled.getStatus()).isEqualTo("inactive");
            verify(service).updateById(existing);
        }
    }

    // ------------------------------------------------------------------
    // handleMessage - 基础校验与对话路由
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("handleMessage - 对话路由")
    class HandleMessageChat {

        @Test
        @DisplayName("渠道不存在时抛 ApiException")
        void shouldThrowWhenChannelMissing() {
            doReturn(null).when(service).getById("c1");

            assertThatThrownBy(() -> service.handleMessage(messageRequest("c1", "你好")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道不存在: c1");
        }

        @Test
        @DisplayName("渠道未启用时拒绝处理")
        void shouldThrowWhenChannelInactive() {
            doReturn(channel("c1", "wechat", "inactive", "app1")).when(service).getById("c1");

            assertThatThrownBy(() -> service.handleMessage(messageRequest("c1", "你好")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道未启用");
        }

        @Test
        @DisplayName("无文本内容（事件）仅落库记录，不调用对话/工作流服务")
        void shouldOnlyRecordWhenMessageBlank() {
            ChannelEntity channel = channel("c1", "wechat", "active", "app1");
            channel.setCallCount(3);
            doReturn(channel).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelReplyDTO request = messageRequest("c1", "   ");
            request.setMsgType("event");
            request.setEventType("subscribe");

            ChannelReplyDTO result = service.handleMessage(request);

            assertThat(result.getChannelId()).isEqualTo("c1");
            assertThat(result.getApplicationId()).isEqualTo("app1");
            assertThat(result.getOpenId()).isEqualTo("open-1");
            assertThat(result.getMsgType()).isEqualTo("event");
            assertThat(result.getEventType()).isEqualTo("subscribe");
            assertThat(result.getMessage()).isNull();

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getChannelId()).isEqualTo("c1");
            assertThat(record.getChannelType()).isEqualTo("wechat");
            assertThat(record.getApplicationId()).isEqualTo("app1");
            assertThat(record.getOpenId()).isEqualTo("open-1");
            assertThat(record.getSessionId()).isNull();
            assertThat(record.getMsgType()).isEqualTo("event");
            assertThat(record.getEventType()).isEqualTo("subscribe");
            assertThat(record.getContent()).isEqualTo("   ");
            assertThat(record.getReplyContent()).isNull();
            assertThat(record.getStatus()).isEqualTo("processed");
            assertThat(record.getErrorMessage()).isNull();
            assertThat(record.getCostTime()).isNotNull().isGreaterThanOrEqualTo(0L);

            // 事件消息不触达下游
            verifyNoInteractions(chatService, workflowService);
            // 调用统计累加
            assertThat(channel.getCallCount()).isEqualTo(4);
            assertThat(channel.getLastCallTime()).isNotNull();
        }

        @Test
        @DisplayName("未关联应用时走对话服务，sessionId 带渠道与会话隔离前缀")
        void shouldRouteToChatWhenApplicationMissing() {
            ChannelEntity channel = channel("c1", "wechat", "active", "app1");
            channel.setCallCount(0);
            doReturn(channel).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));
            when(chatService.chat(any(ChatRequestDTO.class)))
                    .thenReturn(ChatResponseDTO.builder().message("你好呀").build());

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isEqualTo("你好呀");
            assertThat(result.getApplicationId()).isEqualTo("app1");

            ArgumentCaptor<ChatRequestDTO> requestCap = ArgumentCaptor.forClass(ChatRequestDTO.class);
            verify(chatService).chat(requestCap.capture());
            ChatRequestDTO sent = requestCap.getValue();
            assertThat(sent.getApplicationId()).isEqualTo("app1");
            assertThat(sent.getMessage()).isEqualTo("你好");
            assertThat(sent.getSource()).isEqualTo("wechat");
            assertThat(sent.getSessionId()).isEqualTo("ch_c1_open-1");

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("processed");
            assertThat(record.getSessionId()).isEqualTo("ch_c1_open-1");
            assertThat(record.getReplyContent()).isEqualTo("你好呀");
            assertThat(record.getContent()).isEqualTo("你好");

            assertThat(channel.getCallCount()).isEqualTo(1);
            assertThat(channel.getLastCallTime()).isNotNull();
            verifyNoInteractions(workflowService);
        }

        @Test
        @DisplayName("应用类型非 workflow 时同样走对话服务")
        void shouldRouteToChatWhenApplicationIsNotWorkflow() {
            ChannelEntity channel = channel("c1", "feishu", "active", "app-chat");
            doReturn(channel).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-chat");
            application.setType("chat");
            doReturn(application).when(applicationService).getById("app-chat");
            when(chatService.chat(any(ChatRequestDTO.class)))
                    .thenReturn(ChatResponseDTO.builder().message("对话回复").build());

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isEqualTo("对话回复");
            verify(chatService).chat(any(ChatRequestDTO.class));
            verifyNoInteractions(workflowService);
        }

        @Test
        @DisplayName("对话服务返回 null 时回复为空但仍记录 processed")
        void shouldTolerateNullChatResponse() {
            doReturn(channel("c1", "wechat", "active", "app1")).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));
            when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(null);

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isNull();
            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("processed");
            assertThat(record.getReplyContent()).isNull();
        }

        @Test
        @DisplayName("对话服务异常时记录 failed 与错误信息，并包装为 ApiException")
        void shouldRecordFailureWhenChatThrows() {
            ChannelEntity channel = channel("c1", "wechat", "active", "app1");
            channel.setCallCount(0);
            doReturn(channel).when(service).getById("c1");
            when(chatService.chat(any(ChatRequestDTO.class))).thenThrow(new RuntimeException("LLM 超时"));

            assertThatThrownBy(() -> service.handleMessage(messageRequest("c1", "你好")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道消息处理失败")
                    .hasMessageContaining("LLM 超时");

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("failed");
            assertThat(record.getReplyContent()).isNull();
            assertThat(record.getErrorMessage()).contains("LLM 超时");
            assertThat(record.getSessionId()).isEqualTo("ch_c1_open-1");
            // 失败时不更新调用统计
            verify(service, never()).updateById(any(ChannelEntity.class));
            assertThat(channel.getCallCount()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // handleMessage - workflow 路由
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("handleMessage - workflow 路由")
    class HandleMessageWorkflow {

        private ChannelEntity channel;

        @BeforeEach
        void setUpWorkflowApplication() {
            channel = channel("c1", "wechat", "active", "app-wf");
            doReturn(channel).when(service).getById("c1");

            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-wf");
            application.setType("workflow");
            doReturn(application).when(applicationService).getById("app-wf");
        }

        private WorkflowExecutionEntity execution(String status, String outputs) {
            WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
            execution.setStatus(status);
            execution.setOutputs(outputs);
            return execution;
        }

        private void stubWorkflow(String outputs) {
            WorkflowEntity workflow = new WorkflowEntity();
            workflow.setId("wf1");
            doReturn(workflow).when(workflowService).getByApplicationId("app-wf");
            doReturn(execution("completed", outputs)).when(workflowService).execute(eq("wf1"), any(Map.class));
        }

        @Test
        @DisplayName("按 output > result > reply 优先级提取工作流输出，并透传 input/session_id")
        void shouldExtractFirstMatchingOutputKey() {
            stubWorkflow("{\"message\":\"兜底消息\",\"result\":\"优先结果\"}");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isEqualTo("优先结果");

            ArgumentCaptor<Map<String, Object>> inputsCap = ArgumentCaptor.forClass(Map.class);
            verify(workflowService).execute(eq("wf1"), inputsCap.capture());
            assertThat(inputsCap.getValue())
                    .containsEntry("input", "你好")
                    .containsEntry("session_id", "ch_c1_open-1");

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("processed");
            assertThat(record.getReplyContent()).isEqualTo("优先结果");
            assertThat(channel.getCallCount()).isEqualTo(1);
            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("无常见输出变量时兜底取第一个非 __end__ 的文本值")
        void shouldFallbackToFirstTextualField() {
            stubWorkflow("{\"__end__\":\"1\",\"custom\":\"自定义输出\",\"num\":3}");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isEqualTo("自定义输出");
        }

        @Test
        @DisplayName("输出不是 JSON 时原样返回文本")
        void shouldReturnRawOutputWhenNotJson() {
            stubWorkflow("plain output");
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isEqualTo("plain output");
        }

        @Test
        @DisplayName("输出为空时回复为 null 但仍记录 processed")
        void shouldTolerateBlankOutput() {
            stubWorkflow(null);
            doReturn(true).when(service).updateById(any(ChannelEntity.class));

            ChannelReplyDTO result = service.handleMessage(messageRequest("c1", "你好"));

            assertThat(result.getMessage()).isNull();
            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("processed");
            assertThat(record.getReplyContent()).isNull();
        }

        @Test
        @DisplayName("工作流执行失败时包装为 ApiException 并记录 failed")
        void shouldThrowWhenExecutionFailed() {
            WorkflowEntity workflow = new WorkflowEntity();
            workflow.setId("wf1");
            doReturn(workflow).when(workflowService).getByApplicationId("app-wf");
            WorkflowExecutionEntity failed = execution("failed", null);
            failed.setErrorMessage("节点执行异常");
            doReturn(failed).when(workflowService).execute(eq("wf1"), any(Map.class));

            assertThatThrownBy(() -> service.handleMessage(messageRequest("c1", "你好")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("工作流执行失败: 节点执行异常");

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("failed");
            assertThat(record.getErrorMessage()).contains("工作流执行失败");
        }

        @Test
        @DisplayName("应用未关联已发布工作流时抛 ApiException 并记录 failed")
        void shouldThrowWhenWorkflowMissing() {
            doReturn(null).when(workflowService).getByApplicationId("app-wf");

            assertThatThrownBy(() -> service.handleMessage(messageRequest("c1", "你好")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("关联的工作流不存在或未发布");

            ChannelMessageEntity record = captureSavedMessage();
            assertThat(record.getStatus()).isEqualTo("failed");
            assertThat(record.getErrorMessage()).contains("关联的工作流不存在或未发布");
            verifyNoInteractions(chatService);
        }
    }
}
