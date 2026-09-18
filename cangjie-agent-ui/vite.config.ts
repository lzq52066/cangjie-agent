import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { existsSync, renameSync } from 'fs'
import { resolve } from 'path'

/**
 * chat 开发服务的入口是根目录的 chat.html，
 * 访问 /chat/ 时 Vite 默认会返回 index.html（admin 入口），这里改写为 chat.html
 */
function chatDevEntry(): Plugin {
  return {
    name: 'chat-dev-entry',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        // 需保留 base 前缀（/chat/），baseMiddleware 会拦截不以 base 开头的请求
        const m = req.url?.match(/^\/chat\/(index\.html)?(\?.*)?$/)
        if (m) {
          req.url = '/chat/chat.html' + (m[2] || '')
        }
        next()
      })
    }
  }
}

/**
 * 构建产物需落到 index.html，nginx 的 try_files .../index.html 才能命中。
 * chat 模式的入口源文件名为 chat.html，构建后重命名为 index.html。
 */
function renameChatHtml(outDir: string): Plugin {
  return {
    name: 'rename-chat-html',
    closeBundle() {
      const from = resolve(outDir, 'chat.html')
      if (existsSync(from)) {
        renameSync(from, resolve(outDir, 'index.html'))
      }
    }
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isAdmin = mode !== 'chat'
  const base = env.VITE_BASE || (isAdmin ? '/admin/' : '/chat/')
  // 入口必须是 html 而非 main.ts：以 ts 作为 rollup input 时 Vite 不产出 index.html
  const htmlEntry = resolve(__dirname, isAdmin ? 'index.html' : 'chat.html')
  const outDir = resolve(__dirname, isAdmin ? 'dist/admin' : 'dist/chat')

  return {
    base,
    // admin 与 chat 两个 dev server 入口依赖图不同，必须各自独立缓存，
    // 否则会互相覆盖 node_modules/.vite/deps，导致另一方的动态导入 504/404
    cacheDir: resolve(__dirname, 'node_modules/.vite', isAdmin ? 'admin' : 'chat'),
    plugins: [vue(), ...(isAdmin ? [] : [chatDevEntry(), renameChatHtml(outDir)])],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        '@admin': resolve(__dirname, 'src/admin'),
        '@chat': resolve(__dirname, 'src/chat'),
        '@shared': resolve(__dirname, 'src/shared')
      }
    },
    css: {
      preprocessorOptions: {
        scss: { api: 'modern-compiler' }
      }
    },
    build: {
      outDir,
      emptyOutDir: true,
      rollupOptions: {
        input: htmlEntry
      }
    },
    server: {
      host: '0.0.0.0',
      port: isAdmin ? 5173 : 5174,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE || 'http://localhost:8080',
          changeOrigin: true
        },
        '/trigger': {
          target: env.VITE_API_BASE || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
