package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserMessageContributor} 单元测试：本轮输入在内部路径产出、OpenAI 兼容路径让位。
 */
class UserMessageContributorTest {

    private final UserMessageContributor contributor = new UserMessageContributor();

    private ContextRequest request(boolean requestConversation) {
        java.util.Map<String, Object> attributes = new HashMap<>();
        if (requestConversation) {
            attributes.put(ContextAttrs.REQUEST_CONVERSATION, java.util.List.of());
        }
        return ContextRequest.builder().userQuery("你好").attributes(attributes).build();
    }

    @Test
    void metadataShouldDeclareUserSlotAndHighOrder() {
        assertThat(contributor.name()).isEqualTo("userMessage");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.USER);
        assertThat(contributor.getOrder()).isEqualTo(650);
    }

    @Test
    void supportsShouldBeTrueForInternalPath() {
        assertThat(contributor.supports(request(false))).isTrue();
    }

    @Test
    void supportsShouldBeFalseWhenRequestConversationPresent() {
        assertThat(contributor.supports(request(true))).isFalse();
    }

    @Test
    void contributeShouldProduceUserMessageFragment() {
        ContextFragment fragment = contributor.contribute(request(false));

        assertThat(fragment.getSlot()).isEqualTo(ContextSlot.USER);
        assertThat(fragment.getSource()).isEqualTo("userMessage");
        assertThat(fragment.getMessages()).hasSize(1);
        assertThat(fragment.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(fragment.getMessages().get(0).getContent()).isEqualTo("你好");
    }
}
