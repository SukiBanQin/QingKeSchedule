import { cloudflare } from '@cloudflare/vite-plugin'
import { sites } from '@openai/sites-vite-plugin'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue(), sites(), cloudflare({ viteEnvironment: { name: 'server' } })],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
})
