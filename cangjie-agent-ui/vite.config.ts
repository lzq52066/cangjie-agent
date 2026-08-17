import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isAdmin = mode !== 'chat'
  const base = env.VITE_BASE || (isAdmin ? '/admin/' : '/chat/')
  const entry = resolve(__dirname, isAdmin ? 'src/admin/main.ts' : 'src/chat/main.ts')
  const outDir = resolve(__dirname, isAdmin ? 'dist/admin' : 'dist/chat')

  return {
    base,
    plugins: [vue()],
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
