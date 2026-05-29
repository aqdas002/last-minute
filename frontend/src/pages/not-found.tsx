import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md py-12 text-center">
      <p className="text-6xl font-semibold text-zinc-300">404</p>
      <h1 className="mt-4 text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-zinc-600">
        The page you're looking for isn't here. It might have moved or the link is wrong.
      </p>
      <Link
        to="/"
        className="mt-6 inline-block rounded bg-zinc-900 px-4 py-2 text-sm text-white"
      >
        Back to tonight's deals
      </Link>
    </div>
  )
}
