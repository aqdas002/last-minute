import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { editMyListing } from '../api/providers'
import type { Listing } from '../api/listings'

export function EditListingForm({
  listing,
  onClose,
}: {
  listing: Listing
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [title, setTitle] = useState(listing.title)
  const [description, setDescription] = useState(listing.description ?? '')
  const [discounted, setDiscounted] = useState(listing.discountedPriceCents)
  const [capacity, setCapacity] = useState(1)
  const [err, setErr] = useState<string | null>(null)

  const m = useMutation({
    mutationFn: () =>
      editMyListing(listing.id, {
        title,
        description: description || undefined,
        discountedPriceCents: discounted,
        capacity,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-listings'] })
      onClose()
    },
    onError: (e: unknown) => {
      const msg = e instanceof Error ? e.message : 'edit failed'
      if (msg.includes('HAS_ACTIVE_BOOKINGS')) {
        setErr(
          "Can't change price, capacity, or time on a listing with active bookings. Title and description still update.",
        )
      } else {
        setErr(msg)
      }
    },
  })

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        setErr(null)
        m.mutate()
      }}
      className="space-y-2 rounded border border-blue-300 bg-blue-50 p-3"
    >
      <label className="block text-xs">
        <span className="font-medium text-zinc-800">Title</span>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
        />
      </label>
      <label className="block text-xs">
        <span className="font-medium text-zinc-800">Description</span>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
        />
      </label>
      <div className="grid grid-cols-2 gap-2">
        <label className="block text-xs">
          <span className="font-medium text-zinc-800">Discounted price (cents)</span>
          <input
            type="number"
            min={50}
            value={discounted}
            onChange={(e) => setDiscounted(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
          />
        </label>
        <label className="block text-xs">
          <span className="font-medium text-zinc-800">Capacity</span>
          <input
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
          />
        </label>
      </div>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={m.isPending}
          className="rounded bg-zinc-900 px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          {m.isPending ? 'Saving…' : 'Save'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="rounded border border-zinc-300 px-3 py-1 text-xs"
        >
          Cancel
        </button>
      </div>
      {err && <p className="text-xs text-red-700">{err}</p>}
    </form>
  )
}
