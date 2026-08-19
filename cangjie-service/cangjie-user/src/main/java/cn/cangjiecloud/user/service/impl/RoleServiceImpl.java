package cn.cangjiecloud.user.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.RoleMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.service.IRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleEntity>
        implements IRoleService {

    private final UserRoleMapper userRoleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleEntity create(RoleDTO dto) {
        if (!StringUtils.hasText(dto.getStatus())) {
            dto.setStatus("active");
        }
        RoleEntity entity = new RoleEntity();
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus());
        save(entity);
        log.info("角色已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleEntity update(String id, RoleDTO dto) {
        RoleEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("角色不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getCode())) entity.setCode(dto.getCode());
        if (StringUtils.hasText(dto.getDescription())) entity.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getStatus())) entity.setStatus(dto.getStatus());
        updateById(entity);
        log.info("角色已更新: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        RoleEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("角色不存在");
        }
        // 删除关联的用户角色关系
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getRoleId, id));
        removeById(id);
        log.info("角色已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public IPage<RoleEntity> pageQuery(RoleQueryDTO query) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(RoleEntity::getName, query.getKeyword())
                                .or()
                                .like(RoleEntity::getCode, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), RoleEntity::getStatus, query.getStatus())
                .orderByDesc(RoleEntity::getCreateTime);
        Page<RoleEntity> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize());
        return page(page, wrapper);
    }

    @Override
    public List<RoleEntity> listRolesByUserId(String userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId));
        List<String> roleIds = userRoles.stream()
                .map(UserRoleEntity::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return listByIds(roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsersToRole(AssignRoleDTO dto) {
        if (dto.getRoleId() == null || dto.getUserIds() == null || dto.getUserIds().isEmpty()) {
            throw new ApiException("请指定角色和用户列表");
        }
        // 删除该角色下原有的用户关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getRoleId, dto.getRoleId()));
        // 批量新增
        for (String uid : dto.getUserIds()) {
            UserRoleEntity ur = new UserRoleEntity();
            ur.setUserId(uid);
            ur.setRoleId(dto.getRoleId());
            userRoleMapper.insert(ur);
        }
        log.info("角色 {} 已重新分配 {} 个用户", dto.getRoleId(), dto.getUserIds().size());
    }
}