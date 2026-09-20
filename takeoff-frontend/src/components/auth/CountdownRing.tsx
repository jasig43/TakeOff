import { motion } from 'framer-motion'
import { formatClock } from '../../hooks/useCountdown'

interface CountdownRingProps {
  remainingSeconds: number
  /** 1 = full, 0 = empty. */
  progress: number
  expired: boolean
  size?: number
}

/** Circular countdown for OTP validity. Time is also shown as text, so the ring is never the only signal. */
export function CountdownRing({ remainingSeconds, progress, expired, size = 96 }: CountdownRingProps) {
  const stroke = 7
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const urgent = !expired && remainingSeconds <= 30

  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90" aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--line)" strokeWidth={stroke} />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={expired ? 'var(--danger)' : urgent ? 'var(--warning)' : 'var(--brand)'}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={false}
          animate={{ strokeDashoffset: circumference * (1 - progress) }}
          transition={{ duration: 0.25, ease: 'linear' }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className={`font-display text-xl font-bold tabular-nums ${expired ? 'text-danger' : 'text-fg'}`}>
          {expired ? '0:00' : formatClock(remainingSeconds)}
        </span>
        <span className="text-[0.7rem] font-medium uppercase tracking-wide text-muted">{expired ? 'expired' : 'left'}</span>
      </div>
    </div>
  )
}
