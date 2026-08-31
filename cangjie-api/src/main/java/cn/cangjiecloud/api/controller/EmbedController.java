package cn.cangjiecloud.api.controller;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.constant.AppConst;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 应用 iframe 发布页：无需登录、无外部依赖的单文件聊天页面。
 * <p>
 * 企业接入方式一（iframe 嵌入）：
 * {@code <iframe src="http://平台地址/api/open/embed/{appId}"></iframe>}
 * <p>
 * 企业接入方式二（一行挂件）：
 * {@code <script src="http://平台地址/widget/cangjie-widget.js" data-app-id="{appId}"></script>}
 * <p>
 * 依赖网页匿名聊天开关 cangjie.openapi.web-anonymous=true。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping(AppConst.OPEN_API + "/embed")
public class EmbedController {

    private static final Pattern APP_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private final IApplicationService applicationService;

    @Value("${cangjie.openapi.web-anonymous:true}")
    private boolean webAnonymousEnabled;

    @GetMapping(value = "/{appId}", produces = "text/html;charset=UTF-8")
    public String embed(@PathVariable String appId) {
        if (!webAnonymousEnabled) {
            return errorPage("网页匿名聊天已关闭，请联系管理员开启（cangjie.openapi.web-anonymous）");
        }
        if (appId == null || !APP_ID_PATTERN.matcher(appId).matches()) {
            return errorPage("无效的应用标识");
        }
        ApplicationEntity app = applicationService.getById(appId);
        if (app == null || !"published".equals(app.getStatus())) {
            return errorPage("应用不存在或未发布");
        }

        return PAGE_TEMPLATE
                .replace("{TITLE}", escapeHtml(app.getName()))
                .replace("{APP_ID}", appId)
                .replace("{SUGGESTIONS}", sanitizeJson(app.getSuggestions()))
                .replace("{THEME}", "#4f7cff");
    }

    /**
     * HTML 文本转义（防注入）
     */
    private String escapeHtml(String text) {
        if (!StringUtils.hasText(text)) {
            return "AI 助手";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * JSON 数组字符串安全化：非法则回退空数组，并阻断 script 闭合注入
     */
    private String sanitizeJson(String suggestionsJson) {
        if (!StringUtils.hasText(suggestionsJson)) {
            return "[]";
        }
        try {
            com.alibaba.fastjson.JSON.parseArray(suggestionsJson, String.class);
            return suggestionsJson.replace("</", "<\\/");
        } catch (Exception e) {
            return "[]";
        }
    }

    private String errorPage(String message) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>嵌入聊天</title></head>"
                + "<body style=\"font-family:system-ui;display:flex;align-items:center;"
                + "justify-content:center;height:100vh;margin:0;color:#666\">"
                + "<div style=\"text-align:center\">" + escapeHtml(message) + "</div></body></html>";
    }

    /**
     * 单文件嵌入聊天页（无外部依赖）：对接 /api/open/chat/stream 内部 SSE 协议
     */
    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>{TITLE}</title>
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              html, body { height: 100%; }
              body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
                     background: #f5f7fb; display: flex; flex-direction: column; }
              header { background: {THEME}; color: #fff; padding: 12px 16px; font-size: 15px;
                       display: flex; align-items: center; gap: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.15); }
              header .dot { width: 8px; height: 8px; border-radius: 50%; background: #7dffa0; }
              #msgs { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
              .msg { max-width: 82%; padding: 10px 13px; border-radius: 12px; font-size: 14px;
                     line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
              .msg.user { align-self: flex-end; background: {THEME}; color: #fff; border-bottom-right-radius: 4px; }
              .msg.ai { align-self: flex-start; background: #fff; color: #333; border-bottom-left-radius: 4px;
                        box-shadow: 0 1px 3px rgba(0,0,0,.06); }
              .msg.err { background: #fff0f0; color: #c0392b; }
              #quick { padding: 0 16px 8px; display: flex; flex-wrap: wrap; gap: 8px; }
              #quick button { border: 1px solid #d8e0f0; background: #fff; color: #456;
                              border-radius: 16px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
              #quick button:hover { border-color: {THEME}; color: {THEME}; }
              footer { display: flex; gap: 8px; padding: 10px 12px; background: #fff;
                       border-top: 1px solid #e8ecf3; }
              #input { flex: 1; border: 1px solid #dde3ee; border-radius: 10px; padding: 10px 12px;
                       font-size: 14px; outline: none; resize: none; max-height: 96px; font-family: inherit; }
              #input:focus { border-color: {THEME}; }
              #send { background: {THEME}; border: 0; color: #fff; border-radius: 10px;
                      padding: 0 20px; font-size: 14px; cursor: pointer; }
              #send:disabled { opacity: .5; cursor: not-allowed; }
            </style>
            </head>
            <body>
            <header><span class="dot"></span>{TITLE}</header>
            <div id="msgs"></div>
            <div id="quick"></div>
            <footer>
              <textarea id="input" rows="1" placeholder="请输入您的问题…"></textarea>
              <button id="send">发送</button>
            </footer>
            <script id="app-config" type="application/json">{"appId":"{APP_ID}","suggestions":{SUGGESTIONS}}</script>
            <script>
            (function () {
              var cfg = JSON.parse(document.getElementById('app-config').textContent);
              var msgs = document.getElementById('msgs');
              var input = document.getElementById('input');
              var send = document.getElementById('send');
              var quick = document.getElementById('quick');
              var sessionId = null;
              var busy = false;

              function add(role, text) {
                var div = document.createElement('div');
                div.className = 'msg ' + role;
                div.textContent = text;
                msgs.appendChild(div);
                msgs.scrollTop = msgs.scrollHeight;
                return div;
              }

              add('ai', '您好！我是' + document.title + '，请问有什么可以帮您？');
              (cfg.suggestions || []).slice(0, 4).forEach(function (q) {
                var btn = document.createElement('button');
                btn.textContent = q;
                btn.onclick = function () { if (!busy) submit(q); };
                quick.appendChild(btn);
              });

              async function submit(text) {
                if (busy || !text) return;
                busy = true; send.disabled = true;
                add('user', text);
                input.value = '';
                var aiDiv = add('ai', '…');
                try {
                  var resp = await fetch('/api/open/chat/stream', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ applicationId: cfg.appId, sessionId: sessionId,
                                           message: text, source: 'embed' })
                  });
                  if (!resp.ok || !resp.body) { throw new Error('HTTP ' + resp.status); }
                  var reader = resp.body.getReader();
                  var decoder = new TextDecoder('utf-8');
                  var buffer = '', answer = '', started = false;
                  while (true) {
                    var chunk = await reader.read();
                    if (chunk.done) break;
                    buffer += decoder.decode(chunk.value, { stream: true });
                    var blocks = buffer.split('\\n\\n');
                    buffer = blocks.pop();
                    for (var i = 0; i < blocks.length; i++) {
                      var dataLine = blocks[i].split('\\n').find(function (l) { return l.indexOf('data:') === 0; });
                      if (!dataLine) continue;
                      var payload;
                      try { payload = JSON.parse(dataLine.slice(5)); } catch (e) { continue; }
                      if (payload.sessionId) sessionId = payload.sessionId;
                      if (payload.error) { aiDiv.className = 'msg err'; aiDiv.textContent = payload.error; started = true; }
                      if (payload.delta) { aiDiv.textContent = started ? aiDiv.textContent + payload.delta : payload.delta;
                                           started = true; msgs.scrollTop = msgs.scrollHeight; }
                    }
                  }
                  if (!started) { aiDiv.className = 'msg err'; aiDiv.textContent = '本次未能生成回答，请重试'; }
                } catch (err) {
                  aiDiv.className = 'msg err';
                  aiDiv.textContent = '请求失败：' + err.message;
                } finally {
                  busy = false; send.disabled = false; input.focus();
                }
              }

              send.onclick = function () { submit(input.value.trim()); };
              input.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(input.value.trim()); }
              });
              input.focus();
            })();
            </script>
            </body>
            </html>
            """;
}
