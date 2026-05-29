import { api } from './client'

export type Listing = {
  id: string
  title: string
  description: string | null
  city: string | null
  address: string | null
  lat: number | null
  lon: number | null
  originalPriceCents: number
  discountedPriceCents: number
  currency: string
  startTime: string
  endTime: string
  timezone: string
  images: string[]
  categorySlug: string
  categoryName: string
  providerName: string
  status: 'draft' | 'active' | 'suspended' | 'cancelled' | 'expired' | 'sold_out'
}

export type Category = { id: string; slug: string; name: string; iconName: string | null }

export const startingSoon = (city?: string) =>
  api<Listing[]>(`/api/listings${city ? `?city=${encodeURIComponent(city)}` : ''}`)
export const listingById = (id: string) => api<Listing>(`/api/listings/${id}`)
export const byCategory = (slug: string) =>
  api<Listing[]>(`/api/categories/${slug}/listings`)
export const allCategories = () => api<Category[]>('/api/categories')

export const searchListings = (q: string, city?: string, category?: string) => {
  const params = new URLSearchParams({ q })
  if (city) params.set('city', city)
  if (category) params.set('category', category)
  return api<Listing[]>(`/api/listings/search?${params.toString()}`)
}
