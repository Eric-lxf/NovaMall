// Opt-in real backend QA. No request mocks; never used by the production build.
import { defineConfig, mergeConfig } from 'vite'
import baseConfig from '../vite.config.js'

export default defineConfig(context => mergeConfig(baseConfig(context), {
  server: {
    host: '127.0.0.1', port: 15173, strictPort: true, open: false,
    proxy: { '/dev-api': { target: 'http://127.0.0.1:18080' } }
  }
}))
