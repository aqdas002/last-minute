import { api } from './client'

export type BookingStatus = 'pending' | 'confirmed' | 'cancelled' | 'completed' | 'no_show'

export type Booking = {
  id: string
  listingId: string
  listingTitle: string
  status: BookingStatus
  amountPaidCents: number
  currency: string
  startTime: string
  pendingExpiresAt: string
  confirmedAt: string | null
  redemptionCode: string | null
  checkoutUrlIfAny: string | null
}

export const createBooking = (listingId: string) =>
  api<Booking>('/api/bookings', { method: 'POST', body: JSON.stringify({ listingId }) })

export const getBooking = (id: string) => api<Booking>(`/api/bookings/${id}`)

export const myBookings = () => api<Booking[]>('/api/bookings/me')

export const refreshBookingStatus = (id: string) =>
  api<Booking>(`/api/bookings/${id}/refresh-status`, { method: 'POST' })

export type ProviderBooking = {
  id: string
  listingId: string
  listingTitle: string
  consumerEmail: string
  status: BookingStatus
  startTime: string
  confirmedAt: string | null
  redeemedAt: string | null
  redemptionCode: string | null
  amountPaidCents: number
  providerPayoutCents: number
  currency: string
}

export const providerTodayBookings = () =>
  api<ProviderBooking[]>('/api/providers/me/bookings/today')

export const providerAllBookings = () =>
  api<ProviderBooking[]>('/api/providers/me/bookings/all')

export type RevenueSummary = {
  payoutCents: number
  currency: string
  bookingsCount: number
  cancelledCount: number
  windowDays: number
}

export const providerRevenueSummary = () =>
  api<RevenueSummary>('/api/providers/me/bookings/summary')

export type RedemptionResult = {
  code: 'OK' | 'ALREADY_REDEEMED' | 'CODE_NOT_VALID'
  bookingId: string | null
  redeemedAt: string | null
  listingTitle: string | null
}

export const redeemCode = (code: string) =>
  api<RedemptionResult>('/api/providers/me/bookings/redeem', {
    method: 'POST',
    body: JSON.stringify({ code }),
  })

export type RefundReason = 'provider_no_show' | 'quality_issue' | 'duplicate_charge' | 'other'
export type RefundRequestStatus = 'open' | 'approved' | 'denied' | 'auto_resolved'

export type FiledResult = {
  requestId: string
  status: RefundRequestStatus
  createdAt: string
  message: string
}

export type MyRefundRequest = {
  id: string
  reason: RefundReason
  status: RefundRequestStatus
  adminNotes: string | null
  createdAt: string
  resolvedAt: string | null
}

export const fileRefundRequest = (
  bookingId: string,
  reason: RefundReason,
  details?: string,
) =>
  api<FiledResult>(`/api/bookings/${bookingId}/refund-request`, {
    method: 'POST',
    body: JSON.stringify({ reason, details: details ?? null }),
  })

export const getRefundRequests = (bookingId: string) =>
  api<MyRefundRequest[]>(`/api/bookings/${bookingId}/refund-request`)
