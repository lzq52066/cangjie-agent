package cn.cangjiecloud.tool.executor;

import cn.cangjiecloud.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 工具执行器
 */
@Slf4j
public class HttpRequestExecutor {

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public String execute(String url, String method, Map<String, String> headers,
                           String body, Map<String, String> params) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            params.forEach(urlBuilder::addQueryParameter);
        }

        Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        String httpMethod = method != null ? method.toUpperCase() : "GET";
        Request request;
        switch (httpMethod) {
            case "POST":
                request = requestBuilder
                        .post(RequestBody.create(body != null ? body : "{}", MediaType.parse("application/json")))
                        .build();
                break;
            case "PUT":
                request = requestBuilder
                        .put(RequestBody.create(body != null ? body : "{}", MediaType.parse("application/json")))
                        .build();
                break;
            case "DELETE":
                request = requestBuilder
                        .delete(body != null ? RequestBody.create(body, MediaType.parse("application/json")) : null)
                        .build();
                break;
            default:
                request = requestBuilder.get().build();
        }

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("HTTP 工具调用失败: {} {} -> status={}", httpMethod, url, response.code());
                return JsonUtils.toJSONString(Map.of(
                        "status", response.code(),
                        "error", respBody,
                        "success", false
                ));
            }
            return respBody;
        } catch (IOException e) {
            log.error("HTTP 工具执行异常: {} {}", httpMethod, url, e);
            return JsonUtils.toJSONString(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}