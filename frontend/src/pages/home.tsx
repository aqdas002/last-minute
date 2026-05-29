import { useQuery } from '@tanstack/react-query'
import { startingSoon } from '../api/listings'
import { ListingCard } from '../components/listing-card'
import { EmptyState } from '../components/empty-state'

export function HomePage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['starting-soon'],
    queryFn: () => startingSoon(),
  })

  return (
    <div className="space-y-8">
      <section className="rounded-lg border border-zinc-200 bg-zinc-50 px-6 py-8 text-center">
        <h1 className="text-3xl font-semibold">Tonight's deals, before they're gone.</h1>
        <p className="mt-2 text-zinc-600">
          Restaurants, classes, hotels, services — discounted in the last hours before they go to
          waste.
        </p>
      </section>

      <section>
        <h2 className="mb-3 text-xl font-semibold">Starting soon near you</h2>
        {isPending ? (
          <p className="text-zinc-500">Loading…</p>
        ) : isError ? (
          <EmptyState title="Couldn't load listings" hint="Try again in a moment." />
        ) : data && data.length === 0 ? (
          <EmptyState
            title="Nothing starting soon"
            hint="Check back in a bit — providers post deals throughout the day."
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {data!.map(l => (
              <ListingCard key={l.id} l={l} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
