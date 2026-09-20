import { Navigate } from 'react-router-dom'
import { LoginForm } from '../components/auth/LoginForm'
import { PageShell } from '../components/layout/PageShell'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'

/** The landing page: just the sign-in form. Signed-in users go straight to their dashboard. */
export default function LoginPage() {
  usePageTitle('Sign in')
  const { user } = useAuth()

  if (user) {
    return <Navigate to={user.role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard'} replace />
  }
  return (
    <PageShell centered>
      <LoginForm />
    </PageShell>
  )
}
