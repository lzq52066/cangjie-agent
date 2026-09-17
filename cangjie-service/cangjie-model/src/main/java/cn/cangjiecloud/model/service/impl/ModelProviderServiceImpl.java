package cn.cangjiecloud.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.model.api.dto.ModelProviderCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelProviderUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.mapper.ModelProviderMapper;
import cn.cangjiecloud.model.security.ApiKeyCipher;
import cn.cangjiecloud.model.service.IModelProviderService;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProviderEntity>
        implements IModelProviderService {

    private final ApiKeyCipher apiKeyCipher;
    private final ModelMapper modelMapper;
    private final IModelService modelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelProviderEntity create(ModelProviderCreateDTO dto) {
        String code = normalizeCode(dto.getCode());
        assertCodeUnique(code, null);

        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setName(dto.getName());
        entity.setCode(code);
        entity.setBaseUrl(trimToNull(dto.getBaseUrl()));
        entity.setApiKey(StringUtils.hasText(dto.getApiKey()) ? apiKeyCipher.encrypt(dto.getApiKey()) : null);
        entity.setStatus("active");
        entity.setDescription(dto.getDescription());
        save(entity);

        log.info("厂商已创建: {} ({})", entity.getName(), entity.getCode());
        return masked(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelProviderEntity update(String id, ModelProviderUpdateDTO dto) {
        ModelProviderEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("厂商不存在");
        }
        boolean credentialChanged = false;
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getCode())) {
            String code = normalizeCode(dto.getCode());
            assertCodeUnique(code, id);
            credentialChanged |= !code.equals(entity.getCode());
            entity.setCode(code);
        }
        if (dto.getBaseUrl() != null) {
            String baseUrl = trimToNull(dto.getBaseUrl());
            credentialChanged |= !Objects.equals(baseUrl, entity.getBaseUrl());
            entity.setBaseUrl(baseUrl);
        }
        // 含掩码符的 apiKey 为前端回传的脱敏值，跳过更新避免覆盖真实密钥
        if (StringUtils.hasText(dto.getApiKey()) && !dto.getApiKey().contains("*")) {
            entity.setApiKey(apiKeyCipher.encrypt(dto.getApiKey()));
            credentialChanged = true;
        }
        if (StringUtils.hasText(dto.getStatus())) entity.setStatus(dto.getStatus());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        updateById(entity);

        if (credentialChanged) {
            // 凭证变更后失效继承该厂商配置的模型客户端缓存
            modelService.evictClientsOfProvider(id);
        }
        return masked(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ModelProviderEntity entity = getById(id);
        if (entity == null) return;
        Long used = modelMapper.selectCount(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getProviderId, id));
        if (used != null && used > 0) {
            throw new ApiException("该厂商下仍有 " + used + " 个模型，请先在模型中解除关联");
        }
        removeById(id);
        modelService.evictClientsOfProvider(id);
        log.info("厂商已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public IPage<ModelProviderEntity> pageQuery(String keyword, String status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ModelProviderEntity::getName, keyword)
                    .or().like(ModelProviderEntity::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ModelProviderEntity::getStatus, status);
        }
        wrapper.orderByAsc(ModelProviderEntity::getCode);
        // 分页结果逐条脱敏 API Key
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper)
                .convert(this::masked);
    }

    private ModelProviderEntity masked(ModelProviderEntity entity) {
        entity.setApiKey(apiKeyCipher.mask(entity.getApiKey()));
        return entity;
    }

    private void assertCodeUnique(String code, String excludeId) {
        Long exists = lambdaQuery()
                .eq(ModelProviderEntity::getCode, code)
                .ne(excludeId != null, ModelProviderEntity::getId, excludeId)
                .count();
        if (exists != null && exists > 0) {
            throw new ApiException("厂商标识已存在: " + code);
        }
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_-]{1,20}")) {
            throw new ApiException("厂商标识仅支持字母、数字、下划线与短横线（长度 1-20）");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
