import { Navigate } from 'react-router-dom'
import { LoginForm } from '../components/auth/LoginForm'
import { PageShell } from '../components/layout/PageShell'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'

export default function LoginPage() {
  usePageTitle('Driver sign in')
  const { user } = useAuth()

  if (user) {
    return <Navigate to={user.role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard'} replace />
  }
  return (
    <PageShell centered>
      <LoginForm expectedRole="APPLICANT_DRIVER" />
    </PageShell>
  )
}
