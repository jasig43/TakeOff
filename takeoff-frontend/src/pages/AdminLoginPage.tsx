import { Navigate } from 'react-router-dom'
import { LoginForm } from '../components/auth/LoginForm'
import { PageShell } from '../components/layout/PageShell'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'

export default function AdminLoginPage() {
  usePageTitle('Admin portal')
  const { user } = useAuth()

  if (user) {
    return <Navigate to={user.role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard'} replace />
  }
  return (
    <PageShell centered>
      <LoginForm expectedRole="LOGISTICS_ADMIN" />
    </PageShell>
  )
}
