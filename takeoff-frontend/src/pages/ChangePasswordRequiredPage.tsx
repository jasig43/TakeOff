import { Navigate, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { KeyRound, LogOut } from 'lucide-react'
import { ChangePasswordForm } from '../components/account/ChangePasswordForm'
import { PageShell } from '../components/layout/PageShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { useToast } from '../hooks/useToast'
import { dashboardPathFor, homePathFor } from '../utils/homePath'

/**
 * Where someone lands after signing in with a temporary password an administrator issued. Nothing else in the app works
 * until they choose their own password (the server enforces this; ProtectedRoute sends them here). Anyone who does not
 * need to be here is sent on, so this page can never be a dead end.
 */
export default function ChangePasswordRequiredPage() {
  usePageTitle('Choose a new password')
  const { user, logout, updateUser } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  if (!user) return <Navigate to="/" replace />
  if (!user.mustChangePassword) return <Navigate to={dashboardPathFor(user.role)} replace />

  return (
    <PageShell centered>
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        className="mx-auto w-full max-w-lg"
      >
        <GlassCard className="p-6 sm:p-10">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand text-brand-fg">
              <KeyRound className="h-6 w-6" aria-hidden="true" />
            </span>
            <div>
              <h1 className="font-display text-2xl font-bold">Choose your own password</h1>
              <p className="text-sm text-muted">Signed in as {user.email}</p>
            </div>
          </div>

          <p className="mt-6 text-sm">
            You signed in with a temporary password. Choose a password only you know to continue. The temporary one stops
            working as soon as you do.
          </p>

          <div className="mt-6">
            <ChangePasswordForm
              currentLabel="Temporary password"
              submitLabel="Set my password"
              onSuccess={(next) => {
                updateUser(next)
                toast.success('Your password has been set. Welcome to TakeOFF.')
                navigate(homePathFor(next), { replace: true })
              }}
            />
          </div>

          <div className="mt-8 border-t border-line pt-4">
            <Button
              variant="ghost"
              onClick={() => {
                logout()
                navigate('/', { replace: true })
              }}
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign out
            </Button>
          </div>
        </GlassCard>
      </motion.div>
    </PageShell>
  )
}
