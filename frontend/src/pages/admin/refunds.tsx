import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminAttachNotes,
  adminDenyRefundRequest,
  adminListRefundRequests,
  type AdminRefundRequest,
} from '../../api/admin'
import type { RefundRequestStatus } from '../../api/bookings'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

const STATUS_TABS: RefundRequestStatus[] = ['open', 'denied', 'auto_resolved', 'approved']

export function AdminRefundsPage() {
  const [tab, setTab] = useState<RefundRequestStatus>('open')

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['admin-refunds', tab],
    queryFn: () => adminListRefundRequests(tab),
    retry: false,
  })

  if (isError) {
    const msg = error instanceof Error ? error.message : 'unknown'
    return (
      <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
        {msg.includes('403')
          ? "You don't have access to this page."
          : `Could not load refund requests: ${msg}`}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Refund requests</h1>
        <p className="text-sm text-zinc-600">
          Issue actual refunds in the Stripe Dashboard. The webhook auto-closes any open request
          once the refund is processed.
        </p>
      </header>

      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setTab(s)}
            className={`rounded-full border px-3 py-1 text-xs ${
              tab === s
                ? 'border-zinc-900 bg-zinc-900 text-white'
                : 'border-zinc-300 bg-white text-zinc-700'
            }`}
          >
            {s.replace('_', ' ')}
          </button>
        ))}
      </div>

      {isPending && <p className="text-sm text-zinc-500">Loading…</p>}
      {data && data.length === 0 && (
        <p className="text-sm text-zinc-500">No {tab} requests.</p>
      )}
      {data && data.length > 0 && (
        <ul className="space-y-3">
          {data.map((r) => (
            <RefundRow key={r.id} r={r} />
          ))}
        </ul>
      )}
    </div>
  )
}

function RefundRow({ r }: { r: AdminRefundRequest }) {
  const qc = useQueryClient()
  const [notes, setNotes] = useState('')
  const [confirming, setConfirming] = useState(false)

  const denyMut = useMutation({
    mutationFn: () => adminDenyRefundRequest(r.id, notes || '(no reason given)'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-refunds'] })
      setConfirming(false)
      setNotes('')
    },
  })

  const notesMut = useMutation({
    mutationFn: () => adminAttachNotes(r.id, notes),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-refunds'] })
      setNotes('')
    },
  })

  return (
    <li className="rounded border border-zinc-200 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <p className="font-medium">{r.listingTitle}</p>
          <p className="text-xs text-zinc-600">
            {r.consumerEmail} · {money(r.amountPaidCents, r.currency)} · reason: {r.reason}
          </p>
          {r.details && <p className="text-sm text-zinc-700">{r.details}</p>}
          {r.adminNotes && (
            <p className="text-xs italic text-zinc-500">notes: {r.adminNotes}</p>
          )}
          {r.stripePaymentIntentId && (
            <p className="font-mono text-xs text-zinc-500">
              payment_intent: {r.stripePaymentIntentId}
            </p>
          )}
          <p className="text-xs text-zinc-500">
            opened {new Date(r.createdAt).toLocaleString()}
            {r.resolvedAt && ` · resolved ${new Date(r.resolvedAt).toLocaleString()}`}
          </p>
        </div>
      </div>

      {r.status === 'open' && (
        <div className="mt-3 space-y-2">
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
            placeholder="Notes (required to deny)"
            className="block w-full rounded border border-zinc-300 px-2 py-1 text-sm"
          />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => notesMut.mutate()}
              disabled={!notes || notesMut.isPending}
              className="rounded border border-zinc-300 px-3 py-1 text-xs disabled:opacity-50"
            >
              Save notes only
            </button>
            {!confirming ? (
              <button
                type="button"
                onClick={() => setConfirming(true)}
                disabled={!notes}
                className="rounded border border-red-300 bg-red-50 px-3 py-1 text-xs text-red-800 disabled:opacity-50"
              >
                Deny…
              </button>
            ) : (
              <button
                type="button"
                onClick={() => denyMut.mutate()}
                disabled={denyMut.isPending}
                className="rounded bg-red-600 px-3 py-1 text-xs text-white disabled:opacity-50"
              >
                {denyMut.isPending ? 'Denying…' : 'Confirm deny'}
              </button>
            )}
          </div>
        </div>
      )}
    </li>
  )
}
