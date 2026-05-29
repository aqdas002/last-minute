import { useState } from 'react'
import { signupProvider } from '../../api/providers'
import { CommissionDisclosure } from '../../components/commission-disclosure'

export function ProviderSignUpPage() {
  const [email, setEmail] = useState('')
  const [businessName, setBusinessName] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [timezone, setTimezone] = useState('America/New_York')
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle')
  const [errorMsg, setErrorMsg] = useState('')

  return (
    <div className="mx-auto max-w-md py-8">
      <h1 className="text-2xl font-semibold">Sell on Last Minute</h1>
      <p className="mt-1 text-sm text-zinc-600">
        Turn empty seats, rooms, and time slots into revenue tonight.
      </p>

      <div className="mt-6">
        <CommissionDisclosure />
      </div>

      <form
        className="mt-6 space-y-3"
        onSubmit={async e => {
          e.preventDefault()
          setStatus('sending')
          try {
            await signupProvider({ email, businessName, currency, timezone })
            setStatus('sent')
          } catch (err: unknown) {
            setStatus('error')
            setErrorMsg(err instanceof Error ? err.message : 'signup_failed')
          }
        }}
      >
        <label className="block text-sm">
          <span className="text-zinc-700">Business name</span>
          <input
            value={businessName}
            onChange={e => setBusinessName(e.target.value)}
            required
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
            placeholder="Sunset Yoga Studio"
          />
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Email</span>
          <input
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
            placeholder="hello@yourbiz.com"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-zinc-700">Currency</span>
            <select
              value={currency}
              onChange={e => setCurrency(e.target.value)}
              className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
            >
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
              <option value="CAD">CAD</option>
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-zinc-700">Timezone</span>
            <select
              value={timezone}
              onChange={e => setTimezone(e.target.value)}
              className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
            >
              <option value="America/New_York">America/New_York</option>
              <option value="America/Chicago">America/Chicago</option>
              <option value="America/Denver">America/Denver</option>
              <option value="America/Los_Angeles">America/Los_Angeles</option>
              <option value="Europe/London">Europe/London</option>
              <option value="Europe/Berlin">Europe/Berlin</option>
            </select>
          </label>
        </div>

        <button
          type="submit"
          disabled={status === 'sending' || status === 'sent'}
          className="w-full rounded bg-zinc-900 px-3 py-2 text-sm text-white disabled:opacity-50"
        >
          {status === 'sending'
            ? 'Sending…'
            : status === 'sent'
              ? 'Check your email to continue'
              : 'Create my account'}
        </button>
      </form>

      {status === 'sent' && (
        <p className="mt-4 rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
          We've emailed <strong>{email}</strong> a sign-in link. Click it to continue to Stripe
          verification.
        </p>
      )}

      {status === 'error' && (
        <p className="mt-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          Sign-up failed: {errorMsg}. (If this email is already registered, try signing in
          instead.)
        </p>
      )}
    </div>
  )
}
