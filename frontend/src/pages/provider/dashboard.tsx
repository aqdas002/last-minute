import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { onboardingState, myListings } from '../../api/providers'

export function ProviderDashboardPage() {
  const { data: state } = useQuery({
    queryKey: ['onboarding-state'],
    queryFn: () => onboardingState(),
  })
  const { data: listings } = useQuery({
    queryKey: ['my-listings'],
    queryFn: () => myListings(),
  })

  const isLive = state?.chargesEnabled && state?.payoutsEnabled

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Provider dashboard</h1>
        <p className="mt-1 text-sm text-zinc-600">
          {isLive
            ? 'Your account is verified. You can publish listings and accept bookings.'
            : 'Your Stripe verification is still in progress. You can draft listings now and publish them when verification clears.'}
        </p>
      </header>

      <section className="flex items-center gap-3">
        <Link
          to="/provider/listings"
          className="rounded bg-zinc-900 px-4 py-2 text-sm text-white"
        >
          Manage listings
        </Link>
        {!isLive && (
          <Link
            to="/provider/onboarding"
            className="rounded border border-zinc-300 px-4 py-2 text-sm"
          >
            Finish Stripe verification
          </Link>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-zinc-500">Status</h2>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="rounded border border-zinc-200 p-3">
            <dt className="text-zinc-500">Account status</dt>
            <dd className="mt-1 font-medium">{state?.status ?? '—'}</dd>
          </div>
          <div className="rounded border border-zinc-200 p-3">
            <dt className="text-zinc-500">Listings</dt>
            <dd className="mt-1 font-medium">{listings?.length ?? 0}</dd>
          </div>
        </dl>
      </section>
    </div>
  )
}
