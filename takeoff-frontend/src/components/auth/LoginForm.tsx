import { useId, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { LogIn, ShieldCheck } from 'lucide-react'
import { authApi } from '../../api/authApi'
import { isApiError } from '../../api/client'
import type { Role } from '../../api/types'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { isValidEmail } from '../../utils/formValidation'
import { savePendingVerification } from '../../utils/tokenStorage'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { PasswordField } from '../ui/PasswordField'
import { TextField } from '../ui/TextField'
import { OAuthButtons } from './OAuthButtons'

interface LoginFormProps {
  /** Which portal this form belongs to; the returned account's role must match. */
  expectedRole: Role
}

const COPY: Record<Role, { title: string; subtitle: string; wrongRole: string; alt: { to: string; text: string; label: string } }> = {
  APPLICANT_DRIVER: {
    title: 'Driver sign in',
    subtitle: 'Continue your onboarding application.',
    wrongRole: 'That is an administrator account. Please use the Admin Portal to sign in.',
    alt: { to: '/register', text: 'New to TakeOFF?', label: 'Become a driver' },
  },
  LOGISTICS_ADMIN: {
    title: 'Admin portal',
    subtitle: 'Sign in with your logistics administrator account.',
    wrongRole: 'This account does not have administrator access. Drivers can sign in from the driver login.',
    alt: { to: '/login', text: 'Are you a driver?', label: 'Driver sign in' },
  },
}

const dashboardFor = (role: Role) => (role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard')

export function LoginForm({ expectedRole }: LoginFormProps) {
  const navigate = useNavigate()
  const { login } = useAuth()
  const toast = useToast()
  const copy = COPY[expectedRole]

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [emailTouched, setEmailTouched] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)
  const errorId = useId()

  const emailError = emailTouched && !isValidEmail(email) ? 'Enter a valid email address.' : undefined
  const canSubmit = isValidEmail(email) && password.length > 0

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit || inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    setError('')

    const normalizedEmail = email.trim().toLowerCase()
    try {
      const response = await authApi.login({ email: normalizedEmail, password })
      if (response.user.role !== expectedRole) {
        // Do not persist a session for the wrong portal.
        setError(copy.wrongRole)
        return
      }
      login(response)
      navigate(dashboardFor(response.user.role), { replace: true })
    } catch (err) {
      if (isApiError(err) && err.code === 'PHONE_NOT_VERIFIED') {
        savePendingVerification({ email: normalizedEmail })
        toast.info('Please verify your phone number to finish signing in.')
        navigate('/verify-otp', { state: { email: normalizedEmail } })
        return
      }
      if (isApiError(err)) {
        setError(err.message)
        if (err.isNetworkError) toast.error(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
      className="mx-auto w-full max-w-md"
    >
      <GlassCard className="p-6 sm:p-10">
        <div className="flex items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand text-brand-fg">
            {expectedRole === 'LOGISTICS_ADMIN' ? (
              <ShieldCheck className="h-6 w-6" aria-hidden="true" />
            ) : (
              <LogIn className="h-6 w-6" aria-hidden="true" />
            )}
          </span>
          <div>
            <h1 className="font-display text-2xl font-bold">{copy.title}</h1>
            <p className="text-sm text-muted">{copy.subtitle}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} noValidate className="mt-8 space-y-5">
          {error && (
            <p id={errorId} role="alert" className="rounded-xl border border-danger bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
              {error}
            </p>
          )}
          <TextField
            label="Email address"
            type="email"
            required
            autoComplete="email"
            inputMode="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              if (error) setError('')
            }}
            onBlur={() => setEmailTouched(true)}
            error={emailError}
            disabled={submitting}
          />
          <PasswordField
            label="Password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              if (error) setError('')
            }}
            disabled={submitting}
          />
          <Button type="submit" size="lg" fullWidth disabled={!canSubmit} loading={submitting} loadingLabel="Signing you in">
            Sign in
          </Button>
        </form>

        <div className="mt-8 space-y-6">
          <OAuthButtons />
          <p className="text-center text-sm text-muted">
            {copy.alt.text}{' '}
            <Link to={copy.alt.to} className="font-semibold text-brand underline-offset-4 hover:underline">
              {copy.alt.label}
            </Link>
          </p>
        </div>
      </GlassCard>
    </motion.div>
  )
}
