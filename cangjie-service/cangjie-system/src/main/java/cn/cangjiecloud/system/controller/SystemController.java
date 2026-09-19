package cn.cangjiecloud.system.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.system.entity.SystemSettingEntity;
import cn.cangjiecloud.system.mapper.SystemSettingMapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/system")
public class SystemController {

    private final SystemSettingMapper systemSettingMapper;

    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "CangJie Agent");
        info.put("version", "1.0.0");
        info.put("domain", "agent.cangjiecloud.cn");
        info.put("time", System.currentTimeMillis());
        return R.data(info);
    }

    @GetMapping("/setting/{type}")
    public R<ObjectNode> getSetting(@PathVariable Integer type) {
        SystemSettingEntity entity = systemSettingMapper.selectById(type);
        return R.data(entity == null ? JsonUtils.newObject() : entity.getMeta());
    }

    @PutMapping("/setting/{type}")
    public R<Void> saveSetting(@PathVariable Integer type, @RequestBody ObjectNode meta) {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setType(type);
        entity.setMeta(meta);
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdateTime(now);
        SystemSettingEntity exists = systemSettingMapper.selectById(type);
        if (exists == null) {
            entity.setCreateTime(now);
            systemSettingMapper.insert(entity);
        } else {
            entity.setCreateTime(exists.getCreateTime());
            systemSettingMapper.updateById(entity);
        }
        return R.ok();
    }
}
