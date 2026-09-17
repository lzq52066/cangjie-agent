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
              .apv { align-self: flex-start; width: 92%; max-width: 420px; background: #fff;
                     border: 1px solid #f0d9a8; border-left: 3px solid #f59e0b; border-radius: 10px;
                     padding: 12px 14px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
              .apv.dead { opacity: .65; border-left-color: #b8c1d1; }
              .apv-head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
              .apv-risk { padding: 1px 8px; border-radius: 10px; font-size: 12px; white-space: nowrap; }
              .apv-risk.r-high { background: #fdecec; color: #c0392b; }
              .apv-risk.r-mid { background: #fff4e0; color: #b9770e; }
              .apv-risk.r-low { background: #eaf3ff; color: #2f6fd6; }
              .apv-title { font-weight: 600; color: #333; }
              .apv-count { margin-left: auto; color: #b9770e; font-size: 12px; white-space: nowrap; }
              .apv-desc { margin-top: 8px; font-size: 13px; color: #445; }
              .apv-tool { font-family: ui-monospace, Consolas, monospace; background: #f3f5fa;
                          border-radius: 4px; padding: 1px 5px; }
              .apv-reason { margin-top: 6px; font-size: 13px; color: #667; }
              .apv-args { margin-top: 8px; border: 1px solid #eef1f7; border-radius: 8px; overflow: hidden; }
              .apv-args b { display: block; font-size: 12px; font-weight: 500; color: #889;
                            background: #f7f9fc; padding: 4px 8px; }
              .apv-args pre { font-size: 12px; padding: 8px; overflow: auto; max-height: 140px;
                              background: #fbfcfe; white-space: pre-wrap; word-break: break-word;
                              font-family: ui-monospace, Consolas, monospace; }
              .apv-actions { margin-top: 10px; display: flex; gap: 8px; justify-content: flex-end; }
              .apv-btn { border-radius: 8px; padding: 6px 14px; font-size: 13px; cursor: pointer;
                         border: 1px solid transparent; }
              .apv-btn.deny { background: #fff; border-color: #e3b7b7; color: #c0392b; }
              .apv-btn.allow { background: #f59e0b; color: #fff; }
              .apv-btn:disabled { opacity: .5; cursor: not-allowed; }
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

              /* ===== 工具审批（人工在环）===== */
              // 引擎遇到高风险工具时写检查点挂起 run 并推 approval_required 帧，
              // 卡片把决策送回后由后端同步跑完剩余轮次。恢复令牌单次生效，丢了只能重开会话。
              var activeApproval = null;
              var approvalTimer = null;

              function fmtLeft(ms) {
                var s = Math.max(0, Math.floor(ms / 1000));
                var m = Math.floor(s / 60);
                return m + ':' + ('0' + (s % 60)).slice(-2);
              }

              function prettyArgs(raw) {
                if (!raw) return '';
                try { return JSON.stringify(JSON.parse(raw), null, 2); } catch (e) { return String(raw); }
              }

              function renderApprovalCard(a) {
                var card = document.createElement('div');
                card.className = 'apv';
                var head = document.createElement('div');
                head.className = 'apv-head';
                var badge = document.createElement('span');
                badge.className = 'apv-risk ' + (a.risk === 'high' ? 'r-high' : a.risk === 'medium' ? 'r-mid' : 'r-low');
                badge.textContent = a.risk === 'high' ? '高风险' : a.risk === 'medium' ? '中风险' : '低风险';
                var titleEl = document.createElement('span');
                titleEl.className = 'apv-title';
                titleEl.textContent = '需要你的确认';
                var count = document.createElement('span');
                count.className = 'apv-count';
                count.textContent = a.expireAt ? '剩余 ' + fmtLeft(a.expireAt - Date.now()) : '';
                head.appendChild(badge); head.appendChild(titleEl); head.appendChild(count);
                card.appendChild(head);
                var desc = document.createElement('div');
                desc.className = 'apv-desc';
                desc.textContent = '助手请求执行工具 ' + a.tool + (a.toolType ? '（' + a.toolType + '）' : '');
                card.appendChild(desc);
                if (a.reason) {
                  var reason = document.createElement('div');
                  reason.className = 'apv-reason';
                  reason.textContent = a.reason;
                  card.appendChild(reason);
                }
                if (a.arguments) {
                  var args = document.createElement('div');
                  args.className = 'apv-args';
                  var argsLabel = document.createElement('b');
                  argsLabel.textContent = '执行参数';
                  var pre = document.createElement('pre');
                  pre.textContent = prettyArgs(a.arguments);
                  args.appendChild(argsLabel); args.appendChild(pre);
                  card.appendChild(args);
                }
                var actions = document.createElement('div');
                actions.className = 'apv-actions';
                var deny = document.createElement('button');
                deny.className = 'apv-btn deny';
                deny.textContent = '拒绝';
                deny.onclick = function () { decideApproval(a, false); };
                var allow = document.createElement('button');
                allow.className = 'apv-btn allow';
                allow.textContent = '允许执行';
                allow.onclick = function () { decideApproval(a, true); };
                actions.appendChild(deny); actions.appendChild(allow);
                card.appendChild(actions);
                msgs.appendChild(card);
                msgs.scrollTop = msgs.scrollHeight;
                a.card = card; a.count = count; a.deny = deny; a.allow = allow;
              }

              // 收卡：禁用按钮并说明原因；只失效当前活动单，已处理成功的卡片由调用方标记
              function retireApproval(a, note) {
                if (!a || a.dead) return;
                a.dead = true;
                a.card.classList.add('dead');
                a.deny.disabled = true;
                a.allow.disabled = true;
                if (note) a.count.textContent = note;
                if (activeApproval === a) {
                  activeApproval = null;
                  if (approvalTimer) { clearInterval(approvalTimer); approvalTimer = null; }
                }
              }

              function showApproval(raw) {
                if (!raw || !raw.approvalId || !raw.resumeToken) return;
                var a = {
                  approvalId: raw.approvalId,
                  tool: raw.toolName || raw.tool || '未知工具',
                  toolType: raw.toolType || '',
                  arguments: raw.arguments || '',
                  reason: raw.reason || '',
                  risk: (raw.riskLevel || 'low').toLowerCase(),
                  expireAt: raw.expireAt || 0,
                  resumeToken: raw.resumeToken,
                  submitting: false, dead: false
                };
                // 新单到达先失效旧卡，避免误点已被消费的令牌
                retireApproval(activeApproval, '已被新的审批请求替代');
                activeApproval = a;
                renderApprovalCard(a);
                if (!approvalTimer) {
                  approvalTimer = setInterval(function () {
                    if (!activeApproval) return;
                    if (!activeApproval.expireAt) return;
                    var left = activeApproval.expireAt - Date.now();
                    if (left <= 0) {
                      retireApproval(activeApproval, '已超时');
                      add('ai', '审批已超时，本次运行不再恢复，请重新发起对话').classList.add('err');
                    } else {
                      activeApproval.count.textContent = '剩余 ' + fmtLeft(left);
                    }
                  }, 1000);
                }
              }

              async function decideApproval(a, approved) {
                if (!a || a.dead || a.submitting || busy) return;
                var remark = null;
                if (!approved) {
                  // 拒绝原因会作为 tool 消息回喂模型，写清楚它才能换路子继续
                  var v = window.prompt('请填写拒绝原因（会回喂给模型，便于它改用其他方式完成任务）');
                  if (v === null) return;
                  if (!v.trim()) return;
                  remark = v.trim();
                }
                a.submitting = true;
                a.deny.disabled = true; a.allow.disabled = true;
                a.allow.textContent = '处理中…';
                busy = true; send.disabled = true;
                try {
                  var resp = await fetch('/api/open/chat/approval/' + encodeURIComponent(a.approvalId) + '/decide', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ approved: approved, resumeToken: a.resumeToken,
                                           sessionId: sessionId, remark: remark })
                  });
                  var r = await resp.json();
                  if (r.code !== 200) throw new Error(r.msg || ('HTTP ' + resp.status));
                  var data = r.data || {};
                  retireApproval(a, approved ? '✓ 已允许执行' : '✕ 已拒绝');
                  var status = data.status || '';
                  if (status === 'waiting_approval') {
                    showApproval(data.pendingApproval);
                  } else if (status === 'completed') {
                    add('ai', data.message || '（本轮无文本产出）');
                  } else {
                    add('ai', '恢复执行未成功：' + (data.errorMessage || data.finishReason || status || '未知原因'))
                      .classList.add('err');
                  }
                } catch (err) {
                  // 令牌单次生效，失败即收卡，避免重复提交误点
                  retireApproval(a, '处理失败');
                  add('ai', '审批处理失败：' + err.message).classList.add('err');
                } finally {
                  a.submitting = false;
                  busy = false; send.disabled = false; input.focus();
                }
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
                      if (payload.event === 'approval_required') {
                        // 挂起时还没有输出文本则撤掉占位气泡，让审批卡片独立呈现
                        if (!started) aiDiv.remove(); else started = true;
                        showApproval(payload);
                      }
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
