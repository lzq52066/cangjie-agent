package cn.cangjiecloud.user.service;

import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 角色服务接口
 */
public interface IRoleService extends IService<RoleEntity> {

    /**
     * 创建角色
     */
    RoleEntity create(RoleDTO dto);

    /**
     * 更新角色
     */
    RoleEntity update(String id, RoleDTO dto);

    /**
     * 删除角色
     */
    void delete(String id);

    /**
     * 分页查询角色
     */
    IPage<RoleEntity> pageQuery(RoleQueryDTO query);

    /**
     * 根据用户 ID 查询角色列表
     */
    List<RoleEntity> listRolesByUserId(String userId);

    /**
     * 给角色分配用户
     */
    void assignUsersToRole(AssignRoleDTO dto);
}