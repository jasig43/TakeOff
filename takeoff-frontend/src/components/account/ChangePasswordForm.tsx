import { useId, useRef, useState, type FormEvent } from 'react'
import { accountApi } from '../../api/authApi'
import type { UserSummary } from '../../api/types'
import { usePasswordValidation } from '../../hooks/usePasswordValidation'
import { errorMessage, fieldMessages } from '../../utils/apiHelpers'
import { PasswordChecklist } from '../auth/PasswordChecklist'
import { PasswordStrengthMeter } from '../auth/PasswordStrengthMeter'
import { Button } from '../ui/Button'
import { PasswordField } from '../ui/PasswordField'

type Field = 'currentPassword' | 'newPassword'

interface ChangePasswordFormProps {
  /** Called with the refreshed account summary after the password was changed. */
  onSuccess: (user: UserSummary) => void
  /** Label of the first field: "Current password", or "Temporary password" on the forced-change screen. */
  currentLabel?: string
  submitLabel?: string
}

/**
 * Change-your-own-password form, used both in Settings and on the screen that forces a change after signing in with a
 * temporary password. The new password is checked live against the sign-up rules; the server has the final say and its
 * complaints are shown under the field they belong to. Passwords are cleared as soon as they have been sent.
 */
export function ChangePasswordForm({
  onSuccess,
  currentLabel = 'Current password',
  submitLabel = 'Change password',
}: ChangePasswordFormProps) {
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
      const user = await accountApi.changePassword({ currentPassword, newPassword })
      // Never keep plain-text passwords around once they have been sent.
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      onSuccess(user)
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
    <form onSubmit={handleSubmit} noValidate className="space-y-5" aria-describedby={formError ? formErrorId : undefined}>
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
        label={currentLabel}
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
        {submitLabel}
      </Button>
    </form>
  )
}
