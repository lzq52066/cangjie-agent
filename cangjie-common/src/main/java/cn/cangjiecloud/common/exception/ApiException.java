package cn.cangjiecloud.common.exception;

import cn.cangjiecloud.common.api.IResultCode;
import cn.cangjiecloud.common.api.ResultCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final IResultCode resultCode;

    public ApiException(String message) {
        super(message);
        this.resultCode = ResultCode.FAILURE;
    }

    public ApiException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public ApiException(IResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.resultCode = resultCode;
    }

    public ApiException(IResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.resultCode = ResultCode.FAILURE;
    }
}
