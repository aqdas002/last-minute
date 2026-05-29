import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Header } from './components/header'
import { HomePage } from './pages/home'
import { CategoryPage } from './pages/category'
import { ListingPage } from './pages/listing'
import { SignInPage } from './pages/signin'
import { ProviderSignUpPage } from './pages/provider/signup'
import { ProviderOnboardingPage } from './pages/provider/onboarding'
import { ProviderOnboardingReturnPage } from './pages/provider/onboarding-return'
import { ProviderDashboardPage } from './pages/provider/dashboard'
import { ProviderListingsPage } from './pages/provider/listings'

const qc = new QueryClient({
  defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: false } },
})

export default function App() {
  return (
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <Header />
        <main className="mx-auto max-w-5xl px-4 py-6">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/c/:slug" element={<CategoryPage />} />
            <Route path="/l/:id" element={<ListingPage />} />
            <Route path="/signin" element={<SignInPage />} />
            <Route path="/provider/signup" element={<ProviderSignUpPage />} />
            <Route path="/provider/onboarding" element={<ProviderOnboardingPage />} />
            <Route
              path="/provider/onboarding/return"
              element={<ProviderOnboardingReturnPage />}
            />
            <Route path="/provider/dashboard" element={<ProviderDashboardPage />} />
            <Route path="/provider/listings" element={<ProviderListingsPage />} />
            <Route path="*" element={<p>Not found.</p>} />
          </Routes>
        </main>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
