package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 记忆合并判定：对向量近邻命中的"可能重复"记忆，调 LLM 判断两条记忆是否指向同一信息点，
 * 能否归并为一条更完整的表述。独立于纯向量阈值判重，避免把"喜欢美式 / 喜欢拿铁"这类冲突信息误合并。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryMergeService {

    /**
     * 判定两条记忆是否应合并，并返回合并后的内容。
     *
     * @return 合并后的内容；判定为不应合并或调用失败时返回 null
     */
    public String decideMergedContent(OpenAICompatibleClient client, Double temperature,
                                      String existing, String incoming) {
        try {
            String prompt = buildMergePrompt(existing, incoming);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system("你是严谨的用户画像记忆整理助手，只输出 JSON。"),
                            ChatMessage.user(prompt)))
                    .temperature(0.0)
                    .build();
            ChatResponse response = client.chat(request);
            if (response == null || !StringUtils.hasText(response.getContent())) {
                return null;
            }
            String raw = response.getContent().trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JSONObject obj = JSON.parseObject(raw.substring(start, end + 1));
            if (obj == null || !obj.getBooleanValue("merge")) {
                return null;
            }
            String merged = obj.getString("content");
            // 合并结果必须有信息量，且不应只是新记忆的原样复制（那属于判重而非合并）
            if (!StringUtils.hasText(merged)) {
                return null;
            }
            return merged.trim();
        } catch (Exception e) {
            // 判定失败按"不合并"处理，保留两条记忆，不影响主流程
            log.warn("记忆合并判定失败，按不合并处理: {}", e.getMessage());
            return null;
        }
    }

    private String buildMergePrompt(String existing, String incoming) {
        return """
                下面是两条关于同一用户的画像记忆，请判断它们是否在描述同一个信息点。

                记忆A：%s
                记忆B：%s

                判定规则：
                1. 两条记忆指向同一事实/偏好/背景/目标，只是措辞或详略不同（含信息互补），才可合并
                2. 若两条记忆相互矛盾（如 A 说喜欢、B 说不喜欢），不能合并
                3. 若只是主题相关但各说一件事（如"喜欢美式"与"每天喝咖啡"可合并；"喜欢美式"与"喜欢拿铁"不可合并），不能合并
                4. 拿不准时，不合并

                合并时保留两条记忆中的有效信息，写成一句简洁、客观、不超过 50 字的陈述，不要编造未提及的内容。

                只输出 JSON：
                {"merge": true, "content": "合并后的记忆"}
                或
                {"merge": false}
                """.formatted(existing, incoming);
    }
}
