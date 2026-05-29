import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { byCategory } from '../api/listings'
import { ListingCard } from '../components/listing-card'
import { EmptyState } from '../components/empty-state'

export function CategoryPage() {
  const { slug = '' } = useParams<{ slug: string }>()
  const { data, isPending, isError } = useQuery({
    queryKey: ['by-category', slug],
    queryFn: () => byCategory(slug),
    enabled: !!slug,
  })

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold capitalize">{slug.replace('-', ' ')}</h1>
      {isPending ? (
        <p className="text-zinc-500">Loading…</p>
      ) : isError ? (
        <EmptyState title="Couldn't load category" />
      ) : data && data.length === 0 ? (
        <EmptyState title="No deals in this category right now" />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {data!.map(l => (
            <ListingCard key={l.id} l={l} />
          ))}
        </div>
      )}
    </section>
  )
}
