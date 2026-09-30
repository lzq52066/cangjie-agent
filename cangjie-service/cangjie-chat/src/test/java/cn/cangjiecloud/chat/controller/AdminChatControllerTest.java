package cn.cangjiecloud.chat.controller;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminChatController} 单元测试：验证会话分页与会话消息分页两个只读查询入口。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatControllerTest {

    /** 会话服务桩 */
    @Mock
    private IChatSessionService sessionService;

    /** 消息服务桩 */
    @Mock
    private IChatMessageService messageService;

    /** 被测试控制器 */
    private AdminChatController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminChatController(sessionService, messageService);
    }

    /** 会话分页：参数被透传，返回分页结果包装 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnSessionPage() {
        Page<ChatSessionEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(new ChatSessionEntity()));
        when(sessionService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        R<PageResult<ChatSessionEntity>> result = controller.sessions(
                "app-1", "user-1", "s1", "web", "active", "关键", 1, 10);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getList()).hasSize(1);
        verify(sessionService).page(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /** 会话消息分页：按会话 ID 查询并返回正序结果 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnMessagePage() {
        Page<ChatMessageEntity> page = new Page<>(1, 50);
        when(messageService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        R<PageResult<ChatMessageEntity>> result =
                controller.messages("s1", "assistant", "like", "关键", null, null);

        assertThat(result.getData()).isNotNull();
        verify(messageService).page(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
