import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { loadEnv, resetEnvCacheForTests } from './env'

describe('loadEnv', () => {
  const original = process.env

  beforeEach(() => {
    process.env = { ...original }
    resetEnvCacheForTests()              // critical: cache is module-level
  })
  afterEach(() => {
    process.env = original
    resetEnvCacheForTests()
  })

  it('throws when DATABASE_URL is missing', () => {
    delete process.env.DATABASE_URL
    expect(() => loadEnv()).toThrow(/DATABASE_URL/)
  })

  it('throws when AUTH_SECRET is missing', () => {
    process.env.DATABASE_URL = 'postgresql://x'
    delete process.env.AUTH_SECRET
    expect(() => loadEnv()).toThrow(/AUTH_SECRET/)
  })

  it('returns typed env on valid input', () => {
    process.env.DATABASE_URL = 'postgresql://x'
    process.env.AUTH_SECRET = 'abc'
    process.env.APP_URL = 'http://localhost:3000'
    const env = loadEnv()
    expect(env.DATABASE_URL).toBe('postgresql://x')
    expect(env.AUTH_SECRET).toBe('abc')
    expect(env.APP_URL).toBe('http://localhost:3000')
  })
})
