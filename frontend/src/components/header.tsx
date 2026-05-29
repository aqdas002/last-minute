import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { signOut, whoami } from '../api/auth'

export function Header() {
  const qc = useQueryClient()
  const { data: me } = useQuery({
    queryKey: ['whoami'],
    queryFn: whoami,
    staleTime: 60_000,
    retry: false,
  })

  const signOutMut = useMutation({
    mutationFn: signOut,
    onSuccess: () => {
      qc.setQueryData(['whoami'], null)
      qc.invalidateQueries({ queryKey: ['whoami'] })
      window.location.assign('/')
    },
  })

  return (
    <header className="border-b border-zinc-200 px-4 py-3">
      <div className="mx-auto flex max-w-5xl items-center justify-between">
        <Link to="/" className="font-semibold">
          Last Minute
        </Link>
        <nav className="flex items-center gap-3 text-sm">
          {me?.role === 'provider' ? (
            <Link to="/provider/dashboard" className="text-zinc-700 hover:underline">
              Dashboard
            </Link>
          ) : (
            <Link to="/provider/signup" className="text-zinc-700 hover:underline">
              For providers
            </Link>
          )}
          {me && (
            <Link to="/bookings" className="text-zinc-700 hover:underline">
              My bookings
            </Link>
          )}
          {me ? (
            <>
              <span className="text-xs text-zinc-500">{me.email}</span>
              <button
                type="button"
                onClick={() => signOutMut.mutate()}
                disabled={signOutMut.isPending}
                className="rounded border border-zinc-300 px-2 py-1 text-xs disabled:opacity-50"
              >
                {signOutMut.isPending ? 'Signing out…' : 'Sign out'}
              </button>
            </>
          ) : (
            <Link to="/signin" className="rounded border border-zinc-300 px-2 py-1">
              Sign in
            </Link>
          )}
        </nav>
      </div>
    </header>
  )
}
