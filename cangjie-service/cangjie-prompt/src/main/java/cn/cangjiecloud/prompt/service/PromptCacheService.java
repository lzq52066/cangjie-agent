package cn.cangjiecloud.prompt.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 提示词模板缓存服务
 * <p>
 * 使用 Caffeine 本地缓存避免每次对话都查库加载 prompt 模板内容。
 * 缓存 key = templateId，value = template content。
 * 在模板更新/删除时主动失效对应缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptCacheService {

    private final IPromptTemplateService promptTemplateService;

    /**
     * Caffeine 缓存: 10 分钟过期，最大 200 条目
     */
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(200)
            .build();

    /**
     * 获取模板内容（优先缓存，miss 时查库并写入缓存）
     */
    public String getContent(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return null;
        }
        String cached = cache.getIfPresent(templateId);
        if (cached != null) {
            return cached;
        }
        try {
            PromptTemplateEntity template = promptTemplateService.getById(templateId);
            if (template != null && template.getContent() != null) {
                cache.put(templateId, template.getContent());
                return template.getContent();
            }
        } catch (Exception e) {
            log.warn("加载提示词模板失败: {}, {}", templateId, e.getMessage());
        }
        return null;
    }

    /**
     * 主动失效指定模板缓存（在模板更新/删除时调用）
     */
    public void evict(String templateId) {
        if (templateId != null) {
            cache.invalidate(templateId);
            log.debug("提示词模板缓存已失效: {}", templateId);
        }
    }
}