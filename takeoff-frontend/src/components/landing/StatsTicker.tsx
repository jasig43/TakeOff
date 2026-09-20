import { useRef } from 'react'
import { useInView } from 'framer-motion'
import { useCountUp } from '../../hooks/useCountUp'
import { GlassCard } from '../ui/GlassCard'

interface Stat {
  value: number
  suffix?: string
  label: string
  detail: string
  /** "MVP fact" describes what is built; "Goal" is an aspiration, not a measurement. */
  kind: 'MVP fact' | 'Platform goal'
}

const STATS: Stat[] = [
  { value: 9, label: 'Onboarding steps', detail: 'From sign-up to submitted application', kind: 'MVP fact' },
  { value: 6, suffix: '-digit', label: 'Phone verification', detail: 'One-time code sent through RabbitMQ', kind: 'MVP fact' },
  { value: 15, suffix: '+', label: 'Password characters', detail: 'Strict policy enforced client and server', kind: 'MVP fact' },
  { value: 24, suffix: ' h', label: 'Review turnaround', detail: 'Target for admin decisions', kind: 'Platform goal' },
]

function StatTile({ stat, active }: { stat: Stat; active: boolean }) {
  const value = useCountUp(stat.value, { enabled: active })
  return (
    <GlassCard className="p-6 text-center">
      <p className="font-display text-4xl font-extrabold tracking-tight text-fg" aria-label={`${stat.value}${stat.suffix ?? ''}`}>
        <span aria-hidden="true">
          {value}
          {stat.suffix}
        </span>
      </p>
      <p className="mt-2 font-semibold text-fg">{stat.label}</p>
      <p className="mt-1 text-sm text-muted">{stat.detail}</p>
      <span
        className={`mt-3 inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${
          stat.kind === 'Platform goal' ? 'bg-accent-soft text-accent' : 'bg-brand-soft text-brand'
        }`}
      >
        {stat.kind}
      </span>
    </GlassCard>
  )
}

export function StatsTicker() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref, { once: true, margin: '-80px' })

  return (
    <section aria-labelledby="stats-heading" className="py-12">
      <h2 id="stats-heading" className="text-center font-display text-2xl font-bold sm:text-3xl">
        Built for a smoother first mile
      </h2>
      <div ref={ref} className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STATS.map((stat) => (
          <StatTile key={stat.label} stat={stat} active={inView} />
        ))}
      </div>
      <p className="mt-4 text-center text-sm text-muted">
        Demo figures: &ldquo;MVP fact&rdquo; describes how this preview is built, &ldquo;Platform goal&rdquo; is a target.
        None of these are live production statistics.
      </p>
    </section>
  )
}
