import { api } from './client'
import type { Listing } from './listings'

export type SignupBody = {
  email: string
  businessName: string
  currency: string
  timezone: string
}

export type OnboardingState = {
  stripeAccountId: string | null
  chargesEnabled: boolean
  payoutsEnabled: boolean
  status: 'pending_kyc' | 'active' | 'suspended'
}

export type FeePreview = {
  priceCents: number
  platformFeeCents: number
  providerPayoutCents: number
}

export type CreateListingBody = {
  categoryId: string
  title: string
  description?: string
  originalPriceCents: number
  discountedPriceCents: number
  capacity: number
  startTime: string
  endTime: string
  listingExpiresAt: string
}

export const signupProvider = (body: SignupBody) =>
  api<void>('/api/providers/signup', { method: 'POST', body: JSON.stringify(body) })

export const createOnboardingLink = () =>
  api<{ url: string }>('/api/providers/onboarding/link', { method: 'POST' })

export const createDashboardLink = () =>
  api<{ url: string }>('/api/providers/onboarding/dashboard-link', { method: 'POST' })

export const onboardingState = () =>
  api<OnboardingState>('/api/providers/onboarding/state')

export const myListings = () =>
  api<Listing[]>('/api/providers/me/listings')

export const createMyListing = (body: CreateListingBody) =>
  api<Listing>('/api/providers/me/listings', { method: 'POST', body: JSON.stringify(body) })

export const publishListing = (id: string) =>
  api<Listing>(`/api/providers/me/listings/${id}/publish`, { method: 'POST' })

export type EditListingBody = Partial<{
  title: string
  description: string
  originalPriceCents: number
  discountedPriceCents: number
  capacity: number
  startTime: string
  endTime: string
  listingExpiresAt: string
}>

export const editMyListing = (id: string, body: EditListingBody) =>
  api<Listing>(`/api/providers/me/listings/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })

export const suspendListing = (id: string) =>
  api<Listing>(`/api/providers/me/listings/${id}/suspend`, { method: 'POST' })

export const unsuspendListing = (id: string) =>
  api<Listing>(`/api/providers/me/listings/${id}/unsuspend`, { method: 'POST' })

export const previewFee = (priceCents: number) =>
  api<FeePreview>(`/api/providers/me/listings/preview-fee?priceCents=${priceCents}`)
