import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="mx-auto mt-12 max-w-md space-y-3 rounded border border-red-200 bg-red-50 p-4 text-sm text-red-900">
        <h1 className="text-lg font-semibold">Something went wrong</h1>
        <p>The page crashed unexpectedly. Refreshing usually helps.</p>
        <p className="font-mono text-xs text-red-700">{this.state.error.message}</p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="rounded bg-red-700 px-3 py-1 text-xs text-white"
        >
          Reload
        </button>
      </div>
    )
  }
}
