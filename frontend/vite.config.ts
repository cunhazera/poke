/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The SPA calls the API on its own origin (/api/...): in development Vite proxies those calls to the Spring Boot app,
// and in Docker nginx does the same, so the backend needs no CORS configuration.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': apiTarget },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    // Node 25 has its own (file-backed) localStorage global, which shadows jsdom's; the tests need the browser one.
    execArgv: ['--no-experimental-webstorage'],
  },
})
