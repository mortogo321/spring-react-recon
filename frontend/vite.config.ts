/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8080';

/**
 * One config for dev, build and test.
 *
 * Dev proxies the API rather than enabling CORS on the server, so there is one origin in
 * development and one in production (nginx serves the bundle and proxies /api to the container).
 * Nothing about auth behaves differently between them — a whole class of "works locally, 401 in
 * the deployment" bug that is entirely avoidable.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: API_ORIGIN, changeOrigin: true },
      '/actuator': { target: API_ORIGIN, changeOrigin: true },
      '/v3/api-docs': { target: API_ORIGIN, changeOrigin: true },
    },
  },
  // `vite preview` has its own proxy config; the e2e suite serves the built bundle through it, so
  // without this the specs would hit 404 on every /api call.
  preview: {
    port: 4173,
    strictPort: true,
    proxy: {
      '/api': { target: API_ORIGIN, changeOrigin: true },
      '/actuator': { target: API_ORIGIN, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // The data grid alone is ~800 KB unzipped and is loaded on demand by two routes. Warning at
    // 500 KB would fire on every build and train everyone to ignore it.
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        /**
         * MUI, the data grid and the charts dominate the bundle and change on their own release
         * cadence. Splitting them keeps an application-only change from invalidating close to a
         * megabyte of vendor code in every user's cache.
         */
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined;
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/.test(id)) return 'react';
          // Separate chunks: the merchant list needs the grid and no charts, the dashboard needs
          // charts and no grid. One combined vendor chunk would make each page download both.
          if (id.includes('@mui/x-data-grid')) return 'grid';
          if (id.includes('@mui/x-charts') || id.includes('d3-')) return 'charts';
          if (id.includes('@mui/') || id.includes('@emotion/')) return 'mui';
          if (id.includes('@reduxjs/toolkit') || id.includes('react-redux') || id.includes('immer')) return 'redux';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    // A real origin, so document-relative API URLs resolve the way they do in a browser.
    environmentOptions: { jsdom: { url: 'http://localhost:5173/' } },
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // The Playwright specs in e2e/ drive a real browser against a real API; running them under
    // Vitest would start a second, headless, mockless copy of the same suite.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.d.ts'],
    },
  },
});
