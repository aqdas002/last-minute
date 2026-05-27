import { z } from 'zod'

const schema = z.object({
  DATABASE_URL: z.string().url(),
  DIRECT_URL: z.string().url().optional(),
  AUTH_SECRET: z.string().min(1),
  AUTH_GOOGLE_ID: z.string().optional().default(''),
  AUTH_GOOGLE_SECRET: z.string().optional().default(''),
  AUTH_RESEND_KEY: z.string().optional().default(''),
  AUTH_EMAIL_FROM: z.string().email().optional().default('dev@local'),
  SENTRY_DSN: z.string().optional().default(''),
  SENTRY_ENVIRONMENT: z.string().optional().default('development'),
  APP_URL: z.string().url(),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
})

export type Env = z.infer<typeof schema>

let cached: Env | null = null
export function loadEnv(): Env {
  if (cached) return cached
  const result = schema.safeParse(process.env)
  if (!result.success) {
    throw new Error(
      'Invalid environment: ' +
        result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ')
    )
  }
  cached = result.data
  return cached
}

// For tests: clear the cache.
export function resetEnvCacheForTests() { cached = null }
