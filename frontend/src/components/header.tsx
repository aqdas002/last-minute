import { Link } from 'react-router-dom'

export function Header() {
  return (
    <header className="border-b border-zinc-200 px-4 py-3">
      <div className="mx-auto flex max-w-5xl items-center justify-between">
        <Link to="/" className="font-semibold">
          Last Minute
        </Link>
        <nav className="flex items-center gap-3 text-sm">
          <Link to="/bookings" className="text-zinc-700 hover:underline">
            My bookings
          </Link>
          <Link to="/provider/signup" className="text-zinc-700 hover:underline">
            For providers
          </Link>
          <Link to="/signin" className="rounded border border-zinc-300 px-2 py-1">
            Sign in
          </Link>
        </nav>
      </div>
    </header>
  )
}
