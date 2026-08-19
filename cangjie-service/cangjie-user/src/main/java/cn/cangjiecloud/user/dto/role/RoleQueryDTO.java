package cn.cangjiecloud.user.dto.role;

import lombok.Data;

/**
 * 角色查询 DTO
 */
@Data
public class RoleQueryDTO {

    /** 关键词（名称/编码） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 10;
}