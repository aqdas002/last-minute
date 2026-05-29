import { api } from './client'
import type { RefundReason, RefundRequestStatus } from './bookings'

export type AdminRefundRequest = {
  id: string
  bookingId: string
  listingTitle: string
  consumerEmail: string
  reason: RefundReason
  details: string | null
  status: RefundRequestStatus
  adminNotes: string | null
  amountPaidCents: number
  currency: string
  stripePaymentIntentId: string | null
  createdAt: string
  resolvedAt: string | null
}

export const adminListRefundRequests = (status: RefundRequestStatus = 'open') =>
  api<AdminRefundRequest[]>(`/api/admin/refund-requests?status=${status}`)

export const adminDenyRefundRequest = (id: string, notes: string) =>
  api<AdminRefundRequest>(`/api/admin/refund-requests/${id}/deny`, {
    method: 'POST',
    body: JSON.stringify({ notes }),
  })

export const adminAttachNotes = (id: string, notes: string) =>
  api<AdminRefundRequest>(`/api/admin/refund-requests/${id}/notes`, {
    method: 'POST',
    body: JSON.stringify({ notes }),
  })
