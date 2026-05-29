import { Link } from 'react-router-dom'

export function Footer() {
  return (
    <footer className="mt-12 border-t border-zinc-200 px-4 py-6 text-xs text-zinc-500">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p>© {new Date().getFullYear()} Last Minute · all sales final unless provider doesn't honor.</p>
        <nav className="flex gap-4">
          <Link to="/" className="hover:underline">
            Home
          </Link>
          <Link to="/provider/signup" className="hover:underline">
            List on Last Minute
          </Link>
          <a href="https://github.com/aqdas002/last-minute" className="hover:underline" target="_blank" rel="noreferrer">
            Source
          </a>
        </nav>
      </div>
    </footer>
  )
}
