package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.mapper.LlmTraceMapper;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTraceServiceImpl extends ServiceImpl<LlmTraceMapper, LlmTraceEntity>
        implements ILlmTraceService {

    @Override
    public IPage<LlmTraceEntity> pageQuery(LlmTraceQueryDTO query) {
        LambdaQueryWrapper<LlmTraceEntity> wrapper = new LambdaQueryWrapper<LlmTraceEntity>()
                .like(StringUtils.hasText(query.getTraceId()), LlmTraceEntity::getTraceId, query.getTraceId())
                .like(StringUtils.hasText(query.getRequestId()), LlmTraceEntity::getRequestId, query.getRequestId())
                .eq(StringUtils.hasText(query.getAppId()), LlmTraceEntity::getAppId, query.getAppId())
                .like(StringUtils.hasText(query.getAppName()), LlmTraceEntity::getAppName, query.getAppName())
                .eq(StringUtils.hasText(query.getSessionId()), LlmTraceEntity::getSessionId, query.getSessionId())
                .eq(StringUtils.hasText(query.getUserId()), LlmTraceEntity::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getModelId()), LlmTraceEntity::getModelId, query.getModelId())
                .like(StringUtils.hasText(query.getModelName()), LlmTraceEntity::getModelName, query.getModelName())
                .eq(StringUtils.hasText(query.getStatus()), LlmTraceEntity::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getPromptKeyword()), LlmTraceEntity::getPromptContent, query.getPromptKeyword())
                .like(StringUtils.hasText(query.getResponseKeyword()), LlmTraceEntity::getResponseContent, query.getResponseKeyword())
                .ge(query.getStartTime() != null, LlmTraceEntity::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, LlmTraceEntity::getStartTime, query.getEndTime())
                .orderByDesc(LlmTraceEntity::getStartTime);
        return page(new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize()), wrapper);
    }

    @Override
    public LlmTraceEntity getDetail(String id) {
        return getById(id);
    }

    @Override
    public List<LlmTraceEntity> timeline(String sessionId) {
        LambdaQueryWrapper<LlmTraceEntity> wrapper = new LambdaQueryWrapper<LlmTraceEntity>()
                .eq(LlmTraceEntity::getSessionId, sessionId)
                .orderByAsc(LlmTraceEntity::getStartTime);
        return list(wrapper);
    }
}