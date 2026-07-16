import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'
import { resolve, dirname } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

export default defineConfig({
  test: {
    globals: true,
    testTimeout: 30_000,
    hookTimeout: 30_000,
    root: __dirname,
    include: ['**/*.spec.ts'],
    globalSetup: ['./global-setup.ts'],
    reporters: ['default'],
    sequence: {
      concurrent: false,
    },
  },
})
