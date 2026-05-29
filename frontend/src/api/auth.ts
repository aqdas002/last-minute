import { api } from './client'

export const requestMagicLink = (email: string, returnTo?: string) =>
  api<void>('/api/auth/magic/request', {
    method: 'POST',
    body: JSON.stringify({ email, returnTo: returnTo ?? '/' }),
  })

export type WhoAmI = { id: string; email: string; role: 'consumer' | 'provider' | 'admin' }

export const whoami = async (): Promise<WhoAmI | null> => {
  const res = await fetch('/api/auth/me', { credentials: 'include' })
  if (res.status === 204) return null
  if (!res.ok) throw new Error(`whoami_${res.status}`)
  return res.json()
}

export const signOut = () =>
  api<void>('/api/auth/signout', { method: 'POST' })
