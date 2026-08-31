/**
 * CangJie Agent 嵌入挂件（一行接入）
 *
 * 用法：将以下代码粘贴到企业网站 </body> 前
 *   <script src="http://你的平台地址/widget/cangjie-widget.js"
 *           data-app-id="应用ID"
 *           data-title="在线客服"        // 可选，气泡标题
 *           data-color="#4f7cff"         // 可选，主题色
 *           data-welcome="您好，有什么可以帮您？"></script>  // 可选
 *
 * 挂件会渲染一个浮动按钮，点击后以 iframe 打开平台嵌入聊天页，
 * 对话数据经平台 /api/open/chat/stream 流式返回。
 */
(function () {
  'use strict';

  var script = document.currentScript ||
    (function () {
      var all = document.getElementsByTagName('script');
      for (var i = all.length - 1; i >= 0; i--) {
        if ((all[i].src || '').indexOf('cangjie-widget.js') >= 0) return all[i];
      }
      return null;
    })();

  if (!script) { return; }

  var server = script.src.replace(/\/widget\/cangjie-widget\.js.*$/, '');
  var appId = script.getAttribute('data-app-id');
  var title = script.getAttribute('data-title') || 'AI 助手';
  var color = script.getAttribute('data-color') || '#4f7cff';
  var welcome = script.getAttribute('data-welcome') || '';

  if (!appId) {
    console.warn('[cangjie-widget] 缺少 data-app-id 属性');
    return;
  }

  var iframeUrl = server + '/api/open/embed/' + encodeURIComponent(appId) +
    (welcome ? '?w=' + encodeURIComponent(welcome) : '');

  // 浮动按钮
  var btn = document.createElement('button');
  btn.setAttribute('aria-label', title);
  btn.style.cssText = 'position:fixed;right:24px;bottom:24px;width:56px;height:56px;' +
    'border-radius:50%;border:0;cursor:pointer;z-index:2147483000;' +
    'background:' + color + ';color:#fff;font-size:22px;line-height:56px;' +
    'box-shadow:0 4px 16px rgba(0,0,0,.25);transition:transform .15s;';
  btn.textContent = '💬';
  btn.onmouseenter = function () { btn.style.transform = 'scale(1.08)'; };
  btn.onmouseleave = function () { btn.style.transform = 'scale(1)'; };

  // 聊天窗口
  var panel = document.createElement('div');
  panel.style.cssText = 'position:fixed;right:24px;bottom:96px;width:380px;max-width:calc(100vw - 32px);' +
    'height:560px;max-height:calc(100vh - 130px);background:#fff;border-radius:14px;overflow:hidden;' +
    'box-shadow:0 12px 48px rgba(0,0,0,.28);z-index:2147483000;display:none;';

  var iframe = document.createElement('iframe');
  iframe.title = title;
  iframe.style.cssText = 'width:100%;height:100%;border:0;';
  iframe.allow = 'clipboard-write';
  panel.appendChild(iframe);

  var open = false;
  btn.onclick = function () {
    open = !open;
    if (open && !iframe.src) { iframe.src = iframeUrl; }
    panel.style.display = open ? 'block' : 'none';
    btn.textContent = open ? '×' : '💬';
  };

  function mount() {
    document.body.appendChild(panel);
    document.body.appendChild(btn);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
})();
