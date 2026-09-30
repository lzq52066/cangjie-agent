package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.mapper.ChatSessionMapper;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatSessionServiceImpl} 单元测试：覆盖会话列表、按用户过滤、
 * 关闭会话、物理删除（含先删消息）以及按 sessionId 查询。
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    /** MyBatis Mapper 桩 */
    @Mock
    private ChatSessionMapper baseMapper;

    /** 消息服务桩 */
    @Mock
    private IChatMessageService chatMessageService;

    /** 被测试会话服务 */
    private ChatSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatSessionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "chatMessageService", chatMessageService);
    }

    /**
     * 构造会话
     */
    private ChatSessionEntity session(String id, String sessionId, String status) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setStatus(status);
        return entity;
    }

    @Nested
    class Listing {

        /** 按应用列出全部会话 */
        @Test
        void shouldListByApplication() {
            when(baseMapper.selectList(any())).thenReturn(List.of(session("1", "s1", "active")));

            assertThat(service.listByApplication("app-1")).hasSize(1);
        }

        /** userId 空白时直接返回空列表，不查库 */
        @Test
        void shouldReturnEmptyWhenUserIdBlank() {
            assertThat(service.listByApplicationAndUser("app-1", " ")).isEmpty();
        }

        /** 按应用与用户双重过滤 */
        @Test
        void shouldListByApplicationAndUser() {
            when(baseMapper.selectList(any()))
                    .thenReturn(List.of(session("1", "s1", "active")));

            assertThat(service.listByApplicationAndUser("app-1", "u1")).hasSize(1);
        }
    }

    @Nested
    class GetAndClose {

        /** sessionId 空白时返回 null */
        @Test
        void shouldReturnNullForBlankSessionId() {
            assertThat(service.getBySessionId(" ")).isNull();
        }

        /** 命中查询时返回会话实体 */
        @Test
        void shouldReturnSessionWhenFound() {
            when(baseMapper.selectOne(any(), anyBoolean()))
                    .thenReturn(session("1", "s1", "active"));

            assertThat(service.getBySessionId("s1").getSessionId()).isEqualTo("s1");
        }

        /** 会话不存在时关闭抛异常 */
        @Test
        void shouldThrowWhenClosingMissingSession() {
            when(baseMapper.selectOne(any(), anyBoolean())).thenReturn(null);

            assertThatThrownBy(() -> service.close("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("会话不存在");
        }
    }

    /** 关闭命中会话时写入 closed 并更新 */
    @Test
    void shouldUpdateStatusToClosed() {
        when(baseMapper.selectOne(any(), anyBoolean()))
                .thenReturn(session("1", "s1", "active"));

        ChatSessionEntity result = service.close("s1");

        assertThat(result.getStatus()).isEqualTo("closed");
        verify(baseMapper).updateById(any(ChatSessionEntity.class));
    }

    @Nested
    class Delete {

        /** 物理删除：先删消息再删会话 */
        @Test
        void shouldDeleteMessagesThenSession() {
            when(baseMapper.selectOne(any(), anyBoolean()))
                    .thenReturn(session("row-1", "s1", "active"));

            service.deleteBySessionId("s1");

            verify(chatMessageService).deleteBySession("s1");
            verify(baseMapper).deleteById("row-1");
        }

        /** 会话不存在时抛异常且不执行删除 */
        @Test
        void shouldThrowWhenDeletingMissingSession() {
            when(baseMapper.selectOne(any(), anyBoolean())).thenReturn(null);

            assertThatThrownBy(() -> service.deleteBySessionId("missing"))
                    .isInstanceOf(ApiException.class);
        }
    }
}
