import { api } from './client'

export const requestMagicLink = (email: string, returnTo?: string) =>
  api<void>('/api/auth/magic/request', {
    method: 'POST',
    body: JSON.stringify({ email, returnTo: returnTo ?? '/' }),
  })

export type Me = { id: string; email: string; role: 'consumer' | 'provider' | 'admin' }
export const me = () => api<Me>('/api/me')
