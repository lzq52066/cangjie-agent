package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatMessage} 单元测试：builder、静态工厂、无参构造 + setter、equals/hashCode/toString 契约。
 */
class ChatMessageTest {

    @Nested
    class BuilderContract {

        @Test
        void builderShouldPopulateAllFields() {
            ChatMessage message = ChatMessage.builder()
                    .role("user")
                    .content("hello")
                    .toolCallId("call-1")
                    .toolName("weather")
                    .build();

            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isEqualTo("hello");
            assertThat(message.getToolCallId()).isEqualTo("call-1");
            assertThat(message.getToolName()).isEqualTo("weather");
        }

        @Test
        void builderShouldLeaveUnsetFieldsNull() {
            ChatMessage message = ChatMessage.builder().role("system").build();

            assertThat(message.getRole()).isEqualTo("system");
            assertThat(message.getContent()).isNull();
            assertThat(message.getToolCallId()).isNull();
            assertThat(message.getToolName()).isNull();
        }

        @Test
        void allArgsConstructorShouldSetFieldsInDeclarationOrder() {
            ChatMessage message = new ChatMessage("tool", "result", "call-9", "search");

            assertThat(message).isEqualTo(ChatMessage.builder()
                    .role("tool").content("result").toolCallId("call-9").toolName("search").build());
        }
    }

    @Nested
    class StaticFactories {

        @Test
        void systemShouldFixRoleAndContentOnly() {
            ChatMessage message = ChatMessage.system("you are a bot");

            assertThat(message.getRole()).isEqualTo("system");
            assertThat(message.getContent()).isEqualTo("you are a bot");
            assertThat(message.getToolCallId()).isNull();
            assertThat(message.getToolName()).isNull();
        }

        @Test
        void userShouldFixRoleAndAcceptNullContent() {
            ChatMessage message = ChatMessage.user(null);

            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isNull();
        }

        @Test
        void assistantShouldFixRoleAndAcceptEmptyContent() {
            ChatMessage message = ChatMessage.assistant("");

            assertThat(message.getRole()).isEqualTo("assistant");
            assertThat(message.getContent()).isEmpty();
        }

        @Test
        void toolShouldSetRoleContentToolCallIdAndToolName() {
            ChatMessage message = ChatMessage.tool("calculator", "call-42", "6");

            assertThat(message.getRole()).isEqualTo("tool");
            assertThat(message.getContent()).isEqualTo("6");
            assertThat(message.getToolCallId()).isEqualTo("call-42");
            assertThat(message.getToolName()).isEqualTo("calculator");
        }

        @Test
        void toolShouldKeepNullArgsAsNull() {
            ChatMessage message = ChatMessage.tool(null, null, null);

            assertThat(message.getRole()).isEqualTo("tool");
            assertThat(message.getContent()).isNull();
            assertThat(message.getToolCallId()).isNull();
            assertThat(message.getToolName()).isNull();
        }
    }

    @Nested
    class GetterSetterAndEquality {

        @Test
        void noArgsConstructorShouldLeaveAllFieldsNull() {
            ChatMessage message = new ChatMessage();

            assertThat(message.getRole()).isNull();
            assertThat(message.getContent()).isNull();
            assertThat(message.getToolCallId()).isNull();
            assertThat(message.getToolName()).isNull();
        }

        @Test
        void settersShouldUpdateGetters() {
            ChatMessage message = new ChatMessage();
            message.setRole("assistant");
            message.setContent("done");
            message.setToolCallId("id-1");
            message.setToolName("plugin");

            assertThat(message.getRole()).isEqualTo("assistant");
            assertThat(message.getContent()).isEqualTo("done");
            assertThat(message.getToolCallId()).isEqualTo("id-1");
            assertThat(message.getToolName()).isEqualTo("plugin");
        }

        @Test
        void equalsAndHashCodeShouldBeValueBasedAndReflexive() {
            ChatMessage a = ChatMessage.user("q");
            ChatMessage b = ChatMessage.user("q");
            ChatMessage c = ChatMessage.assistant("q");

            assertThat(a).isEqualTo(a);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not-a-message");
            assertThat(a).isNotEqualTo(ChatMessage.user("other"));
            // toolCallId 参与相等性
            assertThat(ChatMessage.tool("t", "1", "c")).isNotEqualTo(ChatMessage.tool("t", "2", "c"));
            assertThat(ChatMessage.tool("t", "1", "c")).isNotEqualTo(ChatMessage.tool("x", "1", "c"));
        }

        @Test
        void toStringShouldContainAllFieldValues() {
            String text = ChatMessage.tool("weather", "call-1", "sunny").toString();

            assertThat(text)
                    .contains("role=tool")
                    .contains("content=sunny")
                    .contains("toolCallId=call-1")
                    .contains("toolName=weather");
        }

        @Test
        void instancesShouldBeUsableAsMapKeysAndListMembers() {
            List<ChatMessage> messages = List.of(ChatMessage.user("a"), ChatMessage.user("a"));

            assertThat(messages).containsExactly(ChatMessage.user("a"), ChatMessage.user("a"));
            assertThat(messages.get(0)).isEqualTo(messages.get(1));
        }
    }
}
