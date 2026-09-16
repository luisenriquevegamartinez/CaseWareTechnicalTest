import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      // Mirrors the tsconfig path alias, so the test resolves the same contract types the
      // components typecheck against.
      '@contract': fileURLToPath(new URL('../contract', import.meta.url)),
    },
  },
  test: {
    globals: true,
    include: ['src/**/*.spec.ts'],
  },
});
