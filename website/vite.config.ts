/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * Vite configuration for the VANTA website (static SPA).
 *
 * - React + Tailwind 4 plugins.
 * - `shared/` at the repository root is read at build time (release manifests), so the dev server
 *   is allowed to serve files from the monorepo root.
 * - React is split into its own long-lived vendor chunk; everything else is code-split per route.
 * - A build manifest is emitted so `scripts/check-bundle-size.mjs` can enforce the gzip budget.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    fs: {
      // The website imports release manifests from ../shared at build time.
      allow: [fileURLToPath(new URL('..', import.meta.url))],
    },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    manifest: true,
    cssMinify: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (
            id.includes('/node_modules/react/') ||
            id.includes('/node_modules/react-dom/') ||
            id.includes('/node_modules/scheduler/')
          ) {
            return 'react-vendor';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
    clearMocks: true,
  },
});
