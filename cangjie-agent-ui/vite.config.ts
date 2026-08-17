import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
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

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isAdmin = mode !== 'chat'
  const base = env.VITE_BASE || (isAdmin ? '/admin/' : '/chat/')
  const entry = resolve(__dirname, isAdmin ? 'src/admin/main.ts' : 'src/chat/main.ts')
  const outDir = resolve(__dirname, isAdmin ? 'dist/admin' : 'dist/chat')

  return {
    base,
    plugins: [vue(), ...(isAdmin ? [] : [chatDevEntry()])],
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
        input: { index: entry }
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
