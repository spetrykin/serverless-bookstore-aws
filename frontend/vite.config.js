import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// Dev-server proxy: the browser calls same-origin `/api/*`, Vite's Node
// process forwards to the real backend. Sidesteps CORS entirely (the
// deployed HttpApi has no CorsConfiguration — see README.md) without
// touching template.yaml/backend.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_BASE,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
