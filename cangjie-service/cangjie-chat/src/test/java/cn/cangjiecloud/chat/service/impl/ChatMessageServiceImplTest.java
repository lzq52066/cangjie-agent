package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.mapper.ChatMessageMapper;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatMessageServiceImpl} 单元测试：覆盖会话消息查询/删除、
 * 反馈合法性校验与人工标注。
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    /** MyBatis Mapper 桩 */
    @Mock
    private ChatMessageMapper baseMapper;

    /** 被测试消息服务 */
    private ChatMessageServiceImpl service;

    /** UserContext 静态桩：单测线程没有 Sa-Token 上下文，getUserId 需静态拦截（不设桩即返回 null） */
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        service = new ChatMessageServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /**
     * 构造 assistant 消息
     */
    private ChatMessageEntity assistantMessage() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId("m1");
        message.setRole("assistant");
        return message;
    }

    @Nested
    class ListAndDelete {

        /** 按会话查询消息时调用 mapper 选择列表 */
        @Test
        void shouldListMessagesBySession() {
            when(baseMapper.selectList(any())).thenReturn(List.of(new ChatMessageEntity()));

            assertThat(service.listBySession("s1")).hasSize(1);
        }

        /** 删除会话消息时调用 mapper 删除 */
        @Test
        void shouldDeleteMessagesBySession() {
            service.deleteBySession("s1");

            verify(baseMapper).delete(any());
        }
    }

    @Nested
    class Feedback {

        /** 非法反馈值抛异常 */
        @Test
        void shouldRejectInvalidFeedback() {
            assertThatThrownBy(() -> service.feedback("m1", "wow"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("反馈值非法，仅支持 like / dislike / none");
        }

        /** null 反馈归一化为 none，合法值更新后返回消息 */
        @Test
        void shouldNormalizeNullToNoneAndUpdate() {
            when(baseMapper.selectById("m1")).thenReturn(assistantMessage());

            ChatMessageEntity result = service.feedback("m1", null);

            assertThat(result.getFeedback()).isEqualTo("none");
            verify(baseMapper).updateById(any(ChatMessageEntity.class));
        }

        /** 大写合法值转小写后保存 */
        @Test
        void shouldLowercaseFeedbackValue() {
            when(baseMapper.selectById("m1")).thenReturn(assistantMessage());

            assertThat(service.feedback("m1", "LIKE").getFeedback()).isEqualTo("like");
        }

        /** 消息不存在时抛异常 */
        @Test
        void shouldThrowWhenMessageMissing() {
            when(baseMapper.selectById("m1")).thenReturn(null);

            assertThatThrownBy(() -> service.feedback("m1", "like"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("消息不存在");
        }

        /** 非 assistant 消息不允许反馈 */
        @Test
        void shouldRejectNonAssistantMessage() {
            ChatMessageEntity userMessage = new ChatMessageEntity();
            userMessage.setRole("user");
            when(baseMapper.selectById("m1")).thenReturn(userMessage);

            assertThatThrownBy(() -> service.feedback("m1", "like"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("仅支持对 AI 回复进行反馈/标注");
        }
    }

    @Nested
    class Annotate {

        /** 标注内容空白时抛异常 */
        @Test
        void shouldRejectBlankAnnotation() {
            assertThatThrownBy(() -> service.annotate("m1", " "))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("标注内容不能为空");
        }

        /** 合法标注写入内容并填充标注时间 */
        @Test
        void shouldSaveAnnotationWithTimestamp() {
            when(baseMapper.selectById("m1")).thenReturn(assistantMessage());

            ChatMessageEntity result = service.annotate("m1", "修正答案");

            assertThat(result.getAnnotation()).isEqualTo("修正答案");
            assertThat(result.getAnnotateTime()).isNotNull();
            verify(baseMapper).updateById(any(ChatMessageEntity.class));
        }
    }
}
