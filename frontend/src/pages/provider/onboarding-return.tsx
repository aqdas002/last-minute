import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { onboardingState } from '../../api/providers'

const POLL_MS = 2000
const MAX_POLLS = 15 // ~30 s

export function ProviderOnboardingReturnPage() {
  const navigate = useNavigate()
  const [polls, setPolls] = useState(0)
  const [state, setState] = useState<'polling' | 'ready' | 'timed_out' | 'error'>('polling')
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    let cancelled = false

    const tick = async () => {
      try {
        const s = await onboardingState()
        if (cancelled) return
        if (s.chargesEnabled && s.payoutsEnabled) {
          setState('ready')
          setTimeout(() => navigate('/provider/dashboard'), 600)
          return
        }
        if (polls + 1 >= MAX_POLLS) {
          setState('timed_out')
          return
        }
        setPolls(p => p + 1)
      } catch (err: unknown) {
        if (!cancelled) {
          setState('error')
          setErrorMsg(err instanceof Error ? err.message : 'state_failed')
        }
      }
    }

    if (state === 'polling') {
      const t = setTimeout(tick, polls === 0 ? 0 : POLL_MS)
      return () => {
        cancelled = true
        clearTimeout(t)
      }
    }
  }, [polls, state, navigate])

  return (
    <div className="mx-auto max-w-md py-8 text-center">
      <h1 className="text-2xl font-semibold">Finishing up…</h1>

      {state === 'polling' && (
        <p className="mt-4 text-sm text-zinc-600">
          Stripe is verifying your details. This usually takes under a minute.
        </p>
      )}

      {state === 'ready' && (
        <p className="mt-4 rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
          You're live. Redirecting to your dashboard…
        </p>
      )}

      {state === 'timed_out' && (
        <p className="mt-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Verification is taking longer than usual. You can leave this page — we'll email you as
          soon as Stripe confirms. Or{' '}
          <a className="underline" href="/provider/dashboard">
            head to your dashboard now
          </a>{' '}
          to draft listings while you wait.
        </p>
      )}

      {state === 'error' && (
        <p className="mt-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          We couldn't check verification status: {errorMsg}. Try refreshing.
        </p>
      )}
    </div>
  )
}
