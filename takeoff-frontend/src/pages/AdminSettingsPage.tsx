import { useId, useRef, useState, type FormEvent } from 'react'
import { KeyRound, Monitor, UserRound } from 'lucide-react'
import { adminApi } from '../api/authApi'
import { PasswordChecklist } from '../components/auth/PasswordChecklist'
import { PasswordStrengthMeter } from '../components/auth/PasswordStrengthMeter'
import { AppShell } from '../components/layout/AppShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { PasswordField } from '../components/ui/PasswordField'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePasswordValidation } from '../hooks/usePasswordValidation'
import { useToast } from '../hooks/useToast'
import { errorMessage, fieldMessages } from '../utils/apiHelpers'

type Field = 'currentPassword' | 'newPassword'

function Row({ label, children }: { label: string; children: string }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="break-all text-sm font-medium text-fg">{children}</dd>
    </div>
  )
}

export default function AdminSettingsPage() {
  usePageTitle('Settings')
  const { user } = useAuth()
  const toast = useToast()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [serverErrors, setServerErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // Synchronous guard: state updates are async, so a fast double-submit could slip past `submitting`.
  const inFlight = useRef(false)

  const strengthId = useId()
  const checklistId = useId()
  const formErrorId = useId()
  const evaluation = usePasswordValidation(newPassword)

  const mismatch = confirmPassword.length > 0 && confirmPassword !== newPassword
  const canSubmit = currentPassword.length > 0 && evaluation.isValid && confirmPassword === newPassword

  const clearError = (field: Field) => {
    setFormError('')
    setServerErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit || inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    setFormError('')
    setServerErrors({})
    try {
      await adminApi.changePassword({ currentPassword, newPassword })
      // Never keep plain-text passwords around once they have been sent.
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      toast.success('Your password has been changed.')
    } catch (error) {
      const fields = fieldMessages(error)
      if (Object.keys(fields).length > 0) {
        setServerErrors({ currentPassword: fields.currentPassword, newPassword: fields.newPassword })
      } else {
        setFormError(errorMessage(error, 'We could not change your password. Please try again.'))
      }
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">Settings</h1>
          <p className="mt-2 text-muted">Your account and how you sign in.</p>
        </header>

        <GlassCard className="p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <UserRound className="h-5 w-5 text-brand" aria-hidden="true" />
            Account
          </h2>
          {user && (
            <dl className="mt-4 space-y-3">
              <Row label="Name">{user.fullName}</Row>
              <Row label="Email">{user.email}</Row>
              <Row label="Phone">{user.phoneNumber}</Row>
              <Row label="Role">Administrator</Row>
            </dl>
          )}
        </GlassCard>

        <GlassCard className="p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <KeyRound className="h-5 w-5 text-brand" aria-hidden="true" />
            Change password
          </h2>
          <p className="mt-1 text-sm text-muted">
            Use the same rules as sign-up. You stay signed in here; other devices that are already signed in stay signed
            in until their session expires.
          </p>

          <form
            onSubmit={handleSubmit}
            noValidate
            className="mt-6 space-y-5"
            aria-describedby={formError ? formErrorId : undefined}
          >
            {formError && (
              <p
                id={formErrorId}
                role="alert"
                className="rounded-xl border border-danger bg-danger-soft px-4 py-3 text-sm font-medium text-danger"
              >
                {formError}
              </p>
            )}

            <PasswordField
              label="Current password"
              required
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => {
                setCurrentPassword(e.target.value)
                clearError('currentPassword')
              }}
              error={serverErrors.currentPassword}
              disabled={submitting}
            />

            <div className="space-y-3">
              <PasswordField
                label="New password"
                required
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => {
                  setNewPassword(e.target.value)
                  clearError('newPassword')
                }}
                error={serverErrors.newPassword}
                describedBy={`${strengthId} ${checklistId}`}
                disabled={submitting}
              />
              <PasswordStrengthMeter id={strengthId} evaluation={evaluation} />
              <PasswordChecklist id={checklistId} rules={evaluation.rules} />
            </div>

            <PasswordField
              label="Confirm new password"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              error={mismatch ? 'The two passwords must match.' : undefined}
              disabled={submitting}
            />

            <Button type="submit" disabled={!canSubmit} loading={submitting} loadingLabel="Changing password">
              Change password
            </Button>
          </form>
        </GlassCard>

        <GlassCard className="p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Monitor className="h-5 w-5 text-brand" aria-hidden="true" />
            Appearance
          </h2>
          <p className="mt-2 text-sm text-muted">
            TakeOFF follows your device&apos;s light or dark setting automatically, so there is nothing to switch here.
          </p>
        </GlassCard>
      </div>
    </AppShell>
  )
}
