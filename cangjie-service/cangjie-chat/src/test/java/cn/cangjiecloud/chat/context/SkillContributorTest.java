package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ISkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillContributor} 单元测试：active 技能拼接为指令段。
 */
class SkillContributorTest {

    private ISkillService skillService;
    private SkillContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        skillService = mock(ISkillService.class);
        contributor = new SkillContributor(skillService);
        attributes = new HashMap<>();
    }

    private ContextRequest request() {
        return ContextRequest.builder().attributes(attributes).build();
    }

    private static SkillEntity skill(String name, String status, String description, String content) {
        SkillEntity entity = new SkillEntity();
        entity.setName(name);
        entity.setStatus(status);
        entity.setDescription(description);
        entity.setContent(content);
        return entity;
    }

    @Test
    void metadataShouldDeclareSystemSlotAndOrder120() {
        assertThat(contributor.name()).isEqualTo("skill");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.SYSTEM);
        assertThat(contributor.getOrder()).isEqualTo(120);
    }

    @Test
    void shouldReturnEmptyWhenSkillIdsMissing() {
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveSkills() {
        attributes.put(ContextAttrs.SKILL_IDS, List.of("skill-1"));
        when(skillService.listByIds(any())).thenReturn(List.of(skill("翻译", "inactive", null, null)));

        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldProduceFragmentContainingSkillNameDescriptionAndContent() {
        attributes.put(ContextAttrs.SKILL_IDS, List.of("skill-1"));
        when(skillService.listByIds(any())).thenReturn(List.of(
                skill("代码审查", "active", "审查代码质量", "按团队规范检查")));

        ContextFragment fragment = contributor.contribute(request());

        assertThat(fragment.getSlot()).isEqualTo(ContextSlot.SYSTEM);
        String prompt = fragment.getMessages().get(0).getContent();
        assertThat(prompt).contains("技能指令").contains("代码审查")
                .contains("描述：审查代码质量").contains("指令：按团队规范检查");
    }

    @Test
    void shouldNotFailWhenDescriptionAndContentMissing() {
        attributes.put(ContextAttrs.SKILL_IDS, List.of("skill-1"));
        when(skillService.listByIds(any())).thenReturn(List.of(skill("极简技能", "active", null, null)));

        ContextFragment fragment = contributor.contribute(request());

        assertThat(fragment.getMessages().get(0).getContent()).contains("极简技能");
    }
}
