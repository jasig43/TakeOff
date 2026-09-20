import { PageShell } from '../components/layout/PageShell'
import { ButtonLink } from '../components/ui/Button'
import { usePageTitle } from '../hooks/usePageTitle'

export default function NotFoundPage() {
  usePageTitle('Page not found')
  return (
    <PageShell centered>
      <div className="text-center">
        <p className="font-display text-7xl font-extrabold text-brand">404</p>
        <h1 className="mt-4 font-display text-2xl font-bold">This route is off the map</h1>
        <p className="mt-2 text-muted">The page you are looking for does not exist or has moved.</p>
        <ButtonLink to="/" className="mt-8">
          Back to home
        </ButtonLink>
      </div>
    </PageShell>
  )
}
