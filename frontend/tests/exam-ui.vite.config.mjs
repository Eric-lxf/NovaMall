import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
const root = path.resolve(import.meta.dirname, '..')
export default defineConfig({ root, plugins: [vue()], optimizeDeps: { entries: ['tests/exam-ui.html'] }, resolve: { alias: [
  { find: '@/utils/request', replacement: path.join(root, 'tests/fixtures/exam-request.js') },
  { find: '@/store/modules/user', replacement: path.join(root, 'tests/fixtures/exam-user.js') },
  { find: '@', replacement: path.join(root, 'src') }
] }, server: { host: '127.0.0.1', port: 5193, strictPort: true, open: false } })
