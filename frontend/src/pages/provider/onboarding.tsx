import { useState } from 'react'
import { createOnboardingLink } from '../../api/providers'

export function ProviderOnboardingPage() {
  const [status, setStatus] = useState<'idle' | 'redirecting' | 'error'>('idle')
  const [errorMsg, setErrorMsg] = useState('')

  return (
    <div className="mx-auto max-w-md py-8">
      <h1 className="text-2xl font-semibold">Connect your bank account</h1>
      <p className="mt-2 text-sm text-zinc-600">
        Stripe handles your identity verification and payouts. You'll spend ~5 minutes confirming
        your business details and bank info, then return here to start listing.
      </p>

      <button
        type="button"
        disabled={status === 'redirecting'}
        onClick={async () => {
          setStatus('redirecting')
          try {
            const { url } = await createOnboardingLink()
            window.location.assign(url)
          } catch (err: unknown) {
            setStatus('error')
            setErrorMsg(err instanceof Error ? err.message : 'link_failed')
          }
        }}
        className="mt-6 rounded bg-zinc-900 px-4 py-2 text-sm text-white disabled:opacity-50"
      >
        {status === 'redirecting' ? 'Opening Stripe…' : 'Continue to Stripe'}
      </button>

      {status === 'error' && (
        <p className="mt-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          Could not start onboarding: {errorMsg}.
        </p>
      )}
    </div>
  )
}
