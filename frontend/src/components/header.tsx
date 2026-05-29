import { Link } from 'react-router-dom'

export function Header() {
  return (
    <header className="border-b border-zinc-200 px-4 py-3">
      <div className="mx-auto flex max-w-5xl items-center justify-between">
        <Link to="/" className="font-semibold">
          Last Minute
        </Link>
        <nav className="text-sm">
          <Link
            to="/signin"
            className="rounded border border-zinc-300 px-2 py-1"
          >
            Sign in
          </Link>
        </nav>
      </div>
    </header>
  )
}
