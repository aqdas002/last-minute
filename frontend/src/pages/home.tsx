import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { searchListings, startingSoon } from '../api/listings'
import { ListingCard } from '../components/listing-card'
import { EmptyState } from '../components/empty-state'

export function HomePage() {
  const [query, setQuery] = useState('')
  const debounced = useDebouncedValue(query, 250)
  const isSearching = debounced.trim().length >= 2

  const { data: soonData, isPending: soonPending, isError: soonError } = useQuery({
    queryKey: ['starting-soon'],
    queryFn: () => startingSoon(),
    enabled: !isSearching,
  })

  const { data: searchData, isPending: searchPending, isError: searchError } = useQuery({
    queryKey: ['search', debounced],
    queryFn: () => searchListings(debounced),
    enabled: isSearching,
  })

  const data = isSearching ? searchData : soonData
  const isPending = isSearching ? searchPending : soonPending
  const isError = isSearching ? searchError : soonError

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
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search restaurants, classes, hotels…"
          className="w-full rounded border border-zinc-300 px-3 py-2 text-sm"
        />
      </section>

      <section>
        <h2 className="mb-3 text-xl font-semibold">
          {isSearching ? `Results for "${debounced}"` : 'Starting soon near you'}
        </h2>
        {isPending ? (
          <p className="text-zinc-500">Loading…</p>
        ) : isError ? (
          <EmptyState title="Couldn't load listings" hint="Try again in a moment." />
        ) : data && data.length === 0 ? (
          <EmptyState
            title={isSearching ? 'No matching deals' : 'Nothing starting soon'}
            hint={
              isSearching
                ? 'Try fewer or different words.'
                : 'Check back in a bit — providers post deals throughout the day.'
            }
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {data!.map((l) => (
              <ListingCard key={l.id} l={l} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function useDebouncedValue<T>(value: T, ms: number): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
