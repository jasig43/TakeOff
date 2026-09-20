import { useId, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { UserPlus } from 'lucide-react'
import { authApi } from '../api/authApi'
import { isApiError } from '../api/client'
import { PasswordChecklist } from '../components/auth/PasswordChecklist'
import { PasswordStrengthMeter } from '../components/auth/PasswordStrengthMeter'
import { PageShell } from '../components/layout/PageShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { PasswordField } from '../components/ui/PasswordField'
import { TextField } from '../components/ui/TextField'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePasswordValidation } from '../hooks/usePasswordValidation'
import { useToast } from '../hooks/useToast'
import { isValidEmail, isValidFullName, normalizePhone, phoneProblem } from '../utils/formValidation'
import { homePathFor } from '../utils/homePath'
import { savePendingVerification } from '../utils/tokenStorage'

type FieldName = 'fullName' | 'email' | 'phoneNumber' | 'password' | 'confirmPassword' | 'termsAccepted'
type FieldErrors = Partial<Record<FieldName, string>>

export default function RegisterPage() {
  usePageTitle('Become a driver')
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [touched, setTouched] = useState<Partial<Record<FieldName, boolean>>>({})
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // Synchronous guard: state updates are async, so a fast double-submit could slip past `submitting`.
  const inFlight = useRef(false)

  const strengthId = useId()
  const checklistId = useId()
  const submitHintId = useId()
  const formErrorId = useId()
  const passwordEval = usePasswordValidation(password)

  if (user) {
    return <Navigate to={homePathFor(user)} replace />
  }

  const clientErrors: FieldErrors = {
    fullName: isValidFullName(fullName) ? undefined : 'Enter your full name (2 to 100 characters).',
    email: isValidEmail(email) ? undefined : 'Enter a valid email address, for example name@example.com.',
    phoneNumber: phoneProblem(phone),
    password: passwordEval.isValid ? undefined : 'Your password does not meet all the requirements yet.',
    confirmPassword:
      confirmPassword.length > 0 && confirmPassword === password ? undefined : 'The two passwords must match.',
    termsAccepted: termsAccepted ? undefined : 'You need to accept the terms to create an account.',
  }
  const formValid = Object.values(clientErrors).every((message) => message === undefined)

  const errorFor = (field: FieldName): string | undefined =>
    serverErrors[field] ?? (touched[field] ? clientErrors[field] : undefined)

  const touch = (field: FieldName) => setTouched((current) => ({ ...current, [field]: true }))
  const clearServerError = (field: FieldName) => {
    setFormError('')
    setServerErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!formValid || inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    setFormError('')
    setServerErrors({})

    const normalizedPhone = normalizePhone(phone)
    const normalizedEmail = email.trim().toLowerCase()

    try {
      const response = await authApi.register({
        fullName: fullName.trim(),
        email: normalizedEmail,
        phoneNumber: normalizedPhone,
        password,
        termsAccepted,
      })

      // Never keep the plain-text password around once it has been sent.
      setPassword('')
      setConfirmPassword('')

      const pending = {
        email: response.email,
        maskedPhone: response.maskedPhone,
        otpExpiresAt: Date.now() + response.otpExpiresInSeconds * 1000,
      }
      savePendingVerification(pending)
      if (!response.otpDispatched) toast.error(response.message)
      navigate('/verify-otp', { state: pending })
    } catch (error) {
      if (!isApiError(error)) {
        setFormError('Something went wrong. Please try again.')
      } else if (error.code === 'EMAIL_ALREADY_REGISTERED') {
        setServerErrors({ email: error.message })
      } else if (error.code === 'PHONE_ALREADY_REGISTERED') {
        setServerErrors({ phoneNumber: error.message })
      } else if (Object.keys(error.fieldErrors).length > 0) {
        const mapped: FieldErrors = {}
        for (const [field, messages] of Object.entries(error.fieldErrors)) {
          mapped[field as FieldName] = messages.join(' ')
        }
        setServerErrors(mapped)
        setFormError('Please fix the highlighted fields and try again.')
      } else {
        setFormError(error.message)
        if (error.isNetworkError) toast.error(error.message)
      }
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <PageShell>
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        className="mx-auto max-w-xl"
      >
        <GlassCard className="p-6 sm:p-10">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand text-brand-fg">
              <UserPlus className="h-6 w-6" aria-hidden="true" />
            </span>
            <div>
              <h1 className="font-display text-2xl font-bold">Become a driver</h1>
              <p className="text-sm text-muted">Step 1 of 2: create your account, then verify your phone.</p>
            </div>
          </div>

          <form onSubmit={handleSubmit} noValidate className="mt-8 space-y-5" aria-describedby={formError ? formErrorId : undefined}>
            {formError && (
              <p
                id={formErrorId}
                role="alert"
                className="rounded-xl border border-danger bg-danger-soft px-4 py-3 text-sm font-medium text-danger"
              >
                {formError}
              </p>
            )}

            <TextField
              label="Full name"
              required
              autoComplete="name"
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value)
                clearServerError('fullName')
              }}
              onBlur={() => touch('fullName')}
              error={errorFor('fullName')}
              disabled={submitting}
            />
            <TextField
              label="Email address"
              type="email"
              required
              autoComplete="email"
              inputMode="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                clearServerError('email')
              }}
              onBlur={() => touch('email')}
              error={errorFor('email')}
              disabled={submitting}
            />
            <TextField
              label="Phone number"
              type="tel"
              required
              autoComplete="tel"
              inputMode="tel"
              placeholder="+15550199"
              hint="International format with country code. We'll send a verification code to this number."
              value={phone}
              onChange={(e) => {
                setPhone(e.target.value)
                clearServerError('phoneNumber')
              }}
              onBlur={() => touch('phoneNumber')}
              error={errorFor('phoneNumber')}
              disabled={submitting}
            />

            <div className="space-y-3">
              <PasswordField
                label="Password"
                required
                autoComplete="new-password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  clearServerError('password')
                }}
                onBlur={() => touch('password')}
                error={errorFor('password')}
                describedBy={`${strengthId} ${checklistId}`}
                disabled={submitting}
              />
              <PasswordStrengthMeter id={strengthId} evaluation={passwordEval} />
              <PasswordChecklist id={checklistId} rules={passwordEval.rules} />
            </div>

            <PasswordField
              label="Confirm password"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value)
                clearServerError('confirmPassword')
              }}
              onBlur={() => touch('confirmPassword')}
              error={errorFor('confirmPassword')}
              disabled={submitting}
            />

            <div>
              <label className="flex cursor-pointer items-start gap-3 text-sm leading-6 text-fg">
                <input
                  type="checkbox"
                  checked={termsAccepted}
                  onChange={(e) => {
                    setTermsAccepted(e.target.checked)
                    touch('termsAccepted')
                  }}
                  aria-invalid={errorFor('termsAccepted') ? true : undefined}
                  aria-describedby={errorFor('termsAccepted') ? 'terms-error' : undefined}
                  disabled={submitting}
                  className="mt-1 h-5 w-5 shrink-0 accent-[var(--brand)]"
                />
                <span>
                  I agree to the TakeOFF terms of service and privacy notice, and I consent to receiving a verification
                  code by SMS.
                </span>
              </label>
              {errorFor('termsAccepted') && (
                <p id="terms-error" className="mt-1.5 text-sm font-medium text-danger">
                  {errorFor('termsAccepted')}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Button type="submit" fullWidth size="lg" disabled={!formValid} loading={submitting} loadingLabel="Creating your account" aria-describedby={submitHintId}>
                Create account
              </Button>
              <p id={submitHintId} className="text-center text-sm text-muted">
                {formValid
                  ? 'Everything looks good. You can create your account.'
                  : 'Complete every field and meet all password requirements to continue.'}
              </p>
            </div>
          </form>

          <p className="mt-8 text-center text-sm text-muted">
            Already have an account?{' '}
            <Link to="/" className="font-semibold text-brand underline-offset-4 hover:underline">
              Sign in
            </Link>
          </p>
        </GlassCard>
      </motion.div>
    </PageShell>
  )
}
