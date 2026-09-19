package cn.cangjiecloud.system.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.typehandler.JSONBTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "system_setting", autoResultMap = true)
public class SystemSettingEntity {

    @TableId
    private Integer type;

    @TableField(typeHandler = JSONBTypeHandler.class)
    private ObjectNode meta;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
