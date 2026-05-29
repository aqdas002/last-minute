import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fileRefundRequest,
  getRefundRequests,
  type RefundReason,
} from '../api/bookings'

const REASON_LABELS: Record<RefundReason, string> = {
  provider_no_show: "Provider didn't show / wouldn't honor",
  quality_issue: 'Quality issue (misleading or unsafe)',
  duplicate_charge: 'I was charged twice',
  other: 'Other',
}

export function RefundRequestButton({
  bookingId,
  bookingStatus,
}: {
  bookingId: string
  bookingStatus: string
}) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<RefundReason>('provider_no_show')
  const [details, setDetails] = useState('')

  const { data: existing } = useQuery({
    queryKey: ['refund-requests', bookingId],
    queryFn: () => getRefundRequests(bookingId),
    enabled: bookingStatus !== 'pending',
  })

  const fileMut = useMutation({
    mutationFn: () => fileRefundRequest(bookingId, reason, details || undefined),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['refund-requests', bookingId] })
      setOpen(false)
      setDetails('')
    },
  })

  if (bookingStatus === 'pending' || bookingStatus === 'cancelled') return null

  const openExisting = existing?.find((r) => r.status === 'open')

  if (openExisting) {
    return (
      <p className="text-xs text-amber-700">
        Refund request open since {new Date(openExisting.createdAt).toLocaleDateString()}.
      </p>
    )
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-xs text-zinc-600 underline"
      >
        Request a refund
      </button>
    )
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        fileMut.mutate()
      }}
      className="space-y-2 rounded border border-amber-300 bg-amber-50 p-3"
    >
      <label className="block text-xs">
        <span className="font-medium text-zinc-800">Reason</span>
        <select
          value={reason}
          onChange={(e) => setReason(e.target.value as RefundReason)}
          className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
        >
          {(Object.keys(REASON_LABELS) as RefundReason[]).map((r) => (
            <option key={r} value={r}>
              {REASON_LABELS[r]}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-xs">
        <span className="font-medium text-zinc-800">What happened? (optional)</span>
        <textarea
          value={details}
          onChange={(e) => setDetails(e.target.value)}
          rows={3}
          maxLength={2000}
          className="mt-1 block w-full rounded border border-zinc-300 bg-white px-2 py-1"
        />
      </label>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={fileMut.isPending}
          className="rounded bg-zinc-900 px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          {fileMut.isPending ? 'Filing…' : 'File request'}
        </button>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="rounded border border-zinc-300 px-3 py-1 text-xs"
        >
          Cancel
        </button>
      </div>
      {fileMut.isError && (
        <p className="text-xs text-red-700">
          {fileMut.error instanceof Error ? fileMut.error.message : 'Could not file'}
        </p>
      )}
    </form>
  )
}
