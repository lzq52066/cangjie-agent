package cn.cangjiecloud.tool.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecuteResultDTO {

    /** 是否执行成功 */
    private Boolean success;

    /** 执行输出 */
    private Object output;

    /** 错误信息 */
    private String error;

    /** 执行耗时（毫秒） */
    private Long executionTime;
}
