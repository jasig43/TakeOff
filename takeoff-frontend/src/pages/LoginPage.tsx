import { Navigate } from 'react-router-dom'
import { LoginForm } from '../components/auth/LoginForm'
import { PageShell } from '../components/layout/PageShell'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { homePathFor } from '../utils/homePath'

/** The landing page: just the sign-in form. Signed-in users go straight to their dashboard (or to choose a password, on a temporary one). */
export default function LoginPage() {
  usePageTitle('Sign in')
  const { user } = useAuth()

  if (user) {
    return <Navigate to={homePathFor(user)} replace />
  }
  return (
    <PageShell centered>
      <LoginForm />
    </PageShell>
  )
}
