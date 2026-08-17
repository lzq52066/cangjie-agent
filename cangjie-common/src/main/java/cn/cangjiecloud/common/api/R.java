package cn.cangjiecloud.common.api;

import lombok.Data;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Optional;

@Data
public class R<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;
    private Long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return buildResult(null, ResultCode.SUCCESS);
    }

    public static <T> R<T> data(T data) {
        return buildResult(data, ResultCode.SUCCESS);
    }

    public static <T> R<T> ok(String msg) {
        R<T> result = buildResult(null, ResultCode.SUCCESS);
        result.setMsg(msg);
        return result;
    }

    public static <T> R<T> fail() {
        return buildResult(null, ResultCode.FAILURE);
    }

    public static <T> R<T> fail(String msg) {
        R<T> result = buildResult(null, ResultCode.FAILURE);
        result.setMsg(msg);
        return result;
    }

    public static <T> R<T> fail(IResultCode resultCode) {
        return buildResult(null, resultCode);
    }

    public static <T> R<T> fail(IResultCode resultCode, String msg) {
        R<T> result = buildResult(null, resultCode);
        result.setMsg(msg);
        return result;
    }

    public static <T> R<T> status(boolean flag) {
        return flag ? ok() : fail();
    }

    private static <T> R<T> buildResult(@Nullable T data, IResultCode resultCode) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMsg(resultCode.getMessage());
        r.setData(data);
        return r;
    }

    public boolean isSuccess() {
        return Optional.ofNullable(this.code).map(c -> ResultCode.SUCCESS.getCode().equals(c)).orElse(false);
    }
}
