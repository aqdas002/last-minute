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
