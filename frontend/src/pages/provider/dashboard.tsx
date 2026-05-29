import { Link } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createDashboardLink, onboardingState, myListings } from '../../api/providers'
import { providerRevenueSummary } from '../../api/bookings'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

export function ProviderDashboardPage() {
  const { data: state } = useQuery({
    queryKey: ['onboarding-state'],
    queryFn: () => onboardingState(),
  })
  const { data: listings } = useQuery({
    queryKey: ['my-listings'],
    queryFn: () => myListings(),
  })
  const { data: summary } = useQuery({
    queryKey: ['provider-summary'],
    queryFn: providerRevenueSummary,
  })

  const isLive = state?.chargesEnabled && state?.payoutsEnabled

  const dashboardMut = useMutation({
    mutationFn: createDashboardLink,
    onSuccess: (r) => window.location.assign(r.url),
  })

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
        <Link
          to="/provider/bookings/today"
          className="rounded border border-zinc-900 px-4 py-2 text-sm text-zinc-900"
        >
          Today's bookings
        </Link>
        <Link
          to="/provider/bookings/all"
          className="rounded border border-zinc-300 px-4 py-2 text-sm text-zinc-700"
        >
          All bookings
        </Link>
        {!isLive && (
          <Link
            to="/provider/onboarding"
            className="rounded border border-zinc-300 px-4 py-2 text-sm"
          >
            Finish Stripe verification
          </Link>
        )}
        {isLive && (
          <button
            type="button"
            disabled={dashboardMut.isPending}
            onClick={() => dashboardMut.mutate()}
            className="rounded border border-zinc-300 px-4 py-2 text-sm disabled:opacity-50"
          >
            {dashboardMut.isPending ? 'Opening Stripe…' : 'View payouts on Stripe'}
          </button>
        )}
      </section>

      {summary && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase text-zinc-500">
            Last {summary.windowDays} days
          </h2>
          <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
            <div className="rounded border border-zinc-200 p-3">
              <dt className="text-zinc-500">Earned</dt>
              <dd className="mt-1 text-lg font-semibold">
                {money(summary.payoutCents, summary.currency)}
              </dd>
            </div>
            <div className="rounded border border-zinc-200 p-3">
              <dt className="text-zinc-500">Bookings</dt>
              <dd className="mt-1 text-lg font-semibold">{summary.bookingsCount}</dd>
            </div>
            <div className="rounded border border-zinc-200 p-3">
              <dt className="text-zinc-500">Cancelled</dt>
              <dd className="mt-1 text-lg font-semibold">{summary.cancelledCount}</dd>
            </div>
          </dl>
        </section>
      )}

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
