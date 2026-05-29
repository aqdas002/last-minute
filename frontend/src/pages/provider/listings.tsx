import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  myListings,
  createMyListing,
  publishListing,
  previewFee,
  type CreateListingBody,
} from '../../api/providers'
import { allCategories } from '../../api/listings'

function money(cents: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

export function ProviderListingsPage() {
  const qc = useQueryClient()
  const { data: listings } = useQuery({ queryKey: ['my-listings'], queryFn: myListings })
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: allCategories })

  const publishMut = useMutation({
    mutationFn: (id: string) => publishListing(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-listings'] }),
  })

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Your listings</h1>
      </header>

      <CreateListingForm
        categories={categories ?? []}
        onCreated={() => qc.invalidateQueries({ queryKey: ['my-listings'] })}
      />

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase text-zinc-500">Current</h2>
        {!listings || listings.length === 0 ? (
          <p className="text-sm text-zinc-500">No listings yet.</p>
        ) : (
          <ul className="space-y-2">
            {listings.map(l => (
              <li
                key={l.id}
                className="flex items-center justify-between gap-3 rounded border border-zinc-200 p-3"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate font-medium">{l.title}</p>
                    <StatusPill status={l.status} />
                  </div>
                  <p className="text-xs text-zinc-500">
                    {l.categoryName} · {money(l.discountedPriceCents, l.currency)} (was{' '}
                    {money(l.originalPriceCents, l.currency)})
                  </p>
                </div>
                {l.status === 'draft' ? (
                  <button
                    disabled={publishMut.isPending}
                    onClick={() => publishMut.mutate(l.id)}
                    className="rounded bg-zinc-900 px-3 py-1 text-sm text-white disabled:opacity-50"
                  >
                    {publishMut.isPending ? 'Publishing…' : 'Publish'}
                  </button>
                ) : (
                  <span className="text-xs text-zinc-400">—</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function StatusPill({ status }: { status: string }) {
  const styles: Record<string, string> = {
    draft: 'bg-zinc-100 text-zinc-700 border-zinc-200',
    active: 'bg-green-50 text-green-800 border-green-200',
    suspended: 'bg-red-50 text-red-800 border-red-200',
    cancelled: 'bg-zinc-100 text-zinc-500 border-zinc-200',
    expired: 'bg-zinc-50 text-zinc-500 border-zinc-200',
    sold_out: 'bg-amber-50 text-amber-800 border-amber-200',
  }
  const cls = styles[status] ?? 'bg-zinc-100 text-zinc-700 border-zinc-200'
  return (
    <span className={`rounded-full border px-2 py-0.5 text-[10px] uppercase ${cls}`}>
      {status}
    </span>
  )
}

function CreateListingForm({
  categories,
  onCreated,
}: {
  categories: { id: string; slug: string; name: string }[]
  onCreated: () => void
}) {
  const [categoryIdState, setCategoryId] = useState<string | null>(null)
  const categoryId = categoryIdState ?? categories[0]?.id ?? ''
  const [title, setTitle] = useState('')
  const [originalPriceCents, setOriginal] = useState(12000)
  const [discountedPriceCents, setDiscounted] = useState(8000)
  const [capacity, setCapacity] = useState(8)
  const [startHoursFromNow, setStartHours] = useState(2)

  const { data: fee } = useQuery({
    queryKey: ['preview-fee', discountedPriceCents],
    queryFn: () => previewFee(discountedPriceCents),
    enabled: discountedPriceCents > 0,
  })

  const createMut = useMutation({
    mutationFn: (body: CreateListingBody) => createMyListing(body),
    onSuccess: () => onCreated(),
  })

  return (
    <section className="rounded border border-zinc-200 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase text-zinc-500">Create a listing</h2>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={e => {
          e.preventDefault()
          const start = new Date(Date.now() + startHoursFromNow * 3600_000)
          const end = new Date(start.getTime() + 60 * 60_000)
          const expiresAt = new Date(start.getTime() - 10 * 60_000)
          createMut.mutate({
            categoryId,
            title,
            originalPriceCents,
            discountedPriceCents,
            capacity,
            startTime: start.toISOString(),
            endTime: end.toISOString(),
            listingExpiresAt: expiresAt.toISOString(),
          })
        }}
      >
        <label className="block text-sm sm:col-span-2">
          <span className="text-zinc-700">Title</span>
          <input
            value={title}
            onChange={e => setTitle(e.target.value)}
            required
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Category</span>
          <select
            value={categoryId}
            onChange={e => setCategoryId(e.target.value)}
            required
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          >
            {categories.map(c => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Capacity</span>
          <input
            type="number"
            min={1}
            value={capacity}
            onChange={e => setCapacity(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Original price (cents)</span>
          <input
            type="number"
            min={50}
            value={originalPriceCents}
            onChange={e => setOriginal(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Discounted price (cents)</span>
          <input
            type="number"
            min={50}
            value={discountedPriceCents}
            onChange={e => setDiscounted(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-zinc-700">Starts in (hours from now)</span>
          <input
            type="number"
            min={0.5}
            step={0.5}
            value={startHoursFromNow}
            onChange={e => setStartHours(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2"
          />
        </label>

        <div className="sm:col-span-2 rounded border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm">
          {fee ? (
            <p>
              <strong>You receive {money(fee.providerPayoutCents)}</strong> per booking after our
              15% ({money(fee.platformFeeCents)}).
            </p>
          ) : (
            <p className="text-zinc-500">Enter a discounted price to see your payout.</p>
          )}
        </div>

        <button
          type="submit"
          disabled={createMut.isPending}
          className="sm:col-span-2 rounded bg-zinc-900 px-3 py-2 text-sm text-white disabled:opacity-50"
        >
          {createMut.isPending ? 'Creating…' : 'Create as draft'}
        </button>

        {createMut.isError && (
          <p className="sm:col-span-2 text-sm text-red-600">
            Create failed:{' '}
            {createMut.error instanceof Error ? createMut.error.message : 'unknown'}
          </p>
        )}
      </form>
    </section>
  )
}
