import { defineConfig } from 'vitest/config'
import path from 'node:path'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'node',
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx', 'tests/**/*.test.ts'],
    exclude: ['tests/integration/**', 'node_modules'],
    coverage: {
      provider: 'v8',
      include: ['src/lib/**', 'src/app/api/**'],
      exclude: [
        '**/*.test.{ts,tsx}',
        'src/lib/db/prisma.ts',          // singleton wrapper
        'src/lib/sentry/**',              // glue to a vendor SDK
      ],
      reporter: ['text', 'lcov'],
      thresholds: {
        // Soft (warn) until M6 — gate flips to hard fail in M6.
        lines: 70,
        functions: 70,
        statements: 70,
        branches: 60,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@/tests': path.resolve(__dirname, './tests'),
    },
  },
})
