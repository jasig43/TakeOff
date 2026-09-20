import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import confetti from 'canvas-confetti'
import { motion } from 'framer-motion'
import { PartyPopper, RefreshCw, Smartphone } from 'lucide-react'
import { authApi } from '../api/authApi'
import { isApiError } from '../api/client'
import { CountdownRing } from '../components/auth/CountdownRing'
import { OTP_LENGTH, OtpInput } from '../components/auth/OtpInput'
import { PageShell } from '../components/layout/PageShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { useAuth } from '../hooks/useAuth'
import { useCountdown } from '../hooks/useCountdown'
import { usePageTitle } from '../hooks/usePageTitle'
import { useToast } from '../hooks/useToast'
import { homePathFor } from '../utils/homePath'
import { readPendingVerification, savePendingVerification, type PendingVerification } from '../utils/tokenStorage'

const DEFAULT_OTP_SECONDS = 300
const RESEND_COOLDOWN_SECONDS = 30
const REDIRECT_DELAY_MS = 1600

function secondsUntil(epochMs: number | undefined): number {
  if (!epochMs) return DEFAULT_OTP_SECONDS
  return Math.max(0, Math.round((epochMs - Date.now()) / 1000))
}

export default function OtpVerificationPage() {
  usePageTitle('Verify your phone')
  const location = useLocation()
  const { user } = useAuth()
  // Only redirect users who arrive already signed in. Capturing this at mount matters: a successful
  // verification signs the user in, and we still want to show the success screen before redirecting.
  const [arrivedSignedIn] = useState(() => user !== null)

  // Router state is the primary source; sessionStorage is the fallback. Neither holds secrets.
  // Snapshotted once: a successful login clears the stored copy, which must not bounce this page.
  const [pending] = useState<PendingVerification | null>(() => {
    const fromRouter = location.state as PendingVerification | null
    return fromRouter?.email ? fromRouter : readPendingVerification()
  })

  if (arrivedSignedIn && user) {
    return <Navigate to={homePathFor(user)} replace />
  }
  if (!pending) {
    return <Navigate to="/register" replace />
  }
  return <OtpVerificationForm pending={pending} />
}

function OtpVerificationForm({ pending }: { pending: PendingVerification }) {
  const navigate = useNavigate()
  const { login } = useAuth()
  const toast = useToast()

  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [verifying, setVerifying] = useState(false)
  const [resending, setResending] = useState(false)
  const [verified, setVerified] = useState(false)
  // Bumping this remounts the input group so focus returns to the first box after an error.
  const [inputEpoch, setInputEpoch] = useState(0)
  const inFlight = useRef(false)
  const errorId = useId()

  const expiry = useCountdown(secondsUntil(pending.otpExpiresAt))
  // A fresh registration just requested a code, so the server would reject an immediate resend.
  const cooldown = useCountdown(pending.otpExpiresAt ? RESEND_COOLDOWN_SECONDS : 0)

  const redirectTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => () => clearTimeout(redirectTimer.current), [])

  const complete = code.length === OTP_LENGTH
  const canVerify = complete && !verifying && !verified

  const handleVerify = async (event: FormEvent) => {
    event.preventDefault()
    if (!canVerify || inFlight.current) return
    inFlight.current = true
    setVerifying(true)
    setError('')
    try {
      const response = await authApi.verifyOtp({ identifier: pending.email, otp: code })
      login(response) // stores the JWT via the token-storage strategy and updates auth state
      setVerified(true)
      confetti({ particleCount: 150, spread: 85, origin: { y: 0.6 }, disableForReducedMotion: true })
      redirectTimer.current = setTimeout(() => navigate('/driver/dashboard', { replace: true }), REDIRECT_DELAY_MS)
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Something went wrong. Please try again.')
      setCode('')
      setInputEpoch((n) => n + 1)
    } finally {
      inFlight.current = false
      setVerifying(false)
    }
  }

  const handleResend = async () => {
    if (resending || cooldown.remainingSeconds > 0) return
    setResending(true)
    setError('')
    try {
      const response = await authApi.resendOtp({ identifier: pending.email })
      expiry.restart(response.otpExpiresInSeconds)
      cooldown.restart(RESEND_COOLDOWN_SECONDS)
      savePendingVerification({ ...pending, otpExpiresAt: Date.now() + response.otpExpiresInSeconds * 1000 })
      setCode('')
      setInputEpoch((n) => n + 1)
      if (response.otpDispatched) toast.success(response.message)
      else toast.error(response.message)
    } catch (err) {
      const message = isApiError(err) ? err.message : 'Could not resend the code. Please try again.'
      if (isApiError(err) && err.status === 429) cooldown.restart(RESEND_COOLDOWN_SECONDS)
      setError(message)
    } finally {
      setResending(false)
    }
  }

  return (
    <PageShell>
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        className="mx-auto max-w-lg"
      >
        <GlassCard className="p-6 text-center sm:p-10">
          {verified ? (
            <div role="status" className="space-y-4 py-6">
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 260, damping: 16 }}
                className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-success-soft text-success"
              >
                <PartyPopper className="h-10 w-10" aria-hidden="true" />
              </motion.span>
              <h1 className="font-display text-2xl font-bold">Phone verified!</h1>
              <p className="text-muted">Taking you to your driver dashboard…</p>
            </div>
          ) : (
            <>
              <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-brand text-brand-fg">
                <Smartphone className="h-7 w-7" aria-hidden="true" />
              </span>
              <h1 className="mt-4 font-display text-2xl font-bold">Verify your phone</h1>
              <p className="mt-2 text-muted">
                Enter the 6-digit code {pending.maskedPhone ? <>sent to <strong className="text-fg">{pending.maskedPhone}</strong></> : 'sent to the phone number on your account'}.
              </p>

              <form onSubmit={handleVerify} className="mt-8 space-y-6">
                <CountdownRing remainingSeconds={expiry.remainingSeconds} progress={expiry.progress} expired={expiry.expired} />
                <p className="sr-only" role="status">
                  {expiry.expired ? 'This code has expired. Request a new one.' : ''}
                </p>

                <OtpInput
                  key={inputEpoch}
                  value={code}
                  onChange={(value) => {
                    setCode(value)
                    if (error) setError('')
                  }}
                  disabled={verifying}
                  hasError={Boolean(error)}
                  describedBy={error ? errorId : undefined}
                  autoFocus
                />

                {error && (
                  <p id={errorId} role="alert" className="rounded-xl border border-danger bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
                    {error}
                  </p>
                )}
                {expiry.expired && !error && (
                  <p className="text-sm font-medium text-warning">This code has expired. Request a new code below.</p>
                )}

                <Button type="submit" size="lg" fullWidth disabled={!canVerify} loading={verifying} loadingLabel="Verifying code">
                  Verify and continue
                </Button>
              </form>

              <div className="mt-6 flex flex-col items-center gap-2 text-sm text-muted">
                <span>Didn&rsquo;t get a code?</span>
                <Button
                  variant="ghost"
                  onClick={handleResend}
                  loading={resending}
                  loadingLabel="Sending a new code"
                  disabled={cooldown.remainingSeconds > 0}
                >
                  <RefreshCw className="h-4 w-4" aria-hidden="true" />
                  {cooldown.remainingSeconds > 0 ? `Resend code in ${cooldown.remainingSeconds}s` : 'Resend code'}
                </Button>
              </div>
            </>
          )}
        </GlassCard>
      </motion.div>
    </PageShell>
  )
}
