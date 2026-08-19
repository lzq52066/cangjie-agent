package cn.cangjiecloud.common.domain;

import lombok.Data;
import java.util.List;

@Data
public class MenuVO {
    private String id;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String type;
    private String status;
    private Integer sort;
    private List<MenuVO> children;
}