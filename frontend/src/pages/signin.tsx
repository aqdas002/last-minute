import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { requestMagicLink } from '../api/auth'

export function SignInPage() {
  const [params] = useSearchParams()
  const error = params.get('error')
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle')

  return (
    <div className="mx-auto max-w-sm py-8">
      <h1 className="text-xl font-semibold">Sign in to Last Minute</h1>
      <p className="mt-1 text-sm text-zinc-600">Catch tonight's deals before they're gone.</p>

      {error && (
        <div className="mt-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error === 'expired'
            ? 'That sign-in link expired. Request a new one.'
            : error === 'not_found'
              ? 'That sign-in link is invalid or already used.'
              : 'Sign-in failed. Try again.'}
        </div>
      )}

      <form
        className="mt-6 space-y-3"
        onSubmit={async e => {
          e.preventDefault()
          setStatus('sending')
          try {
            await requestMagicLink(email, params.get('return_to') ?? '/')
            setStatus('sent')
          } catch {
            setStatus('error')
          }
        }}
      >
        <label className="block text-sm">
          <span className="text-zinc-700">Email</span>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
            placeholder="you@example.com"
          />
        </label>
        <button
          disabled={status === 'sending' || status === 'sent'}
          className="rounded bg-zinc-900 px-3 py-2 text-sm text-white disabled:opacity-50"
        >
          {status === 'sending' ? 'Sending…' : status === 'sent' ? 'Check your email' : 'Send magic link'}
        </button>
      </form>

      {status === 'sent' && (
        <p className="mt-3 text-sm text-zinc-600">
          We've sent a sign-in link to <span className="font-medium">{email}</span>. It expires in
          15 minutes.
        </p>
      )}
    </div>
  )
}
