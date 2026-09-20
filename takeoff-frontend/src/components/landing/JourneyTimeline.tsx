import { motion } from 'framer-motion'
import {
  CarFront,
  ClipboardCheck,
  FileUp,
  IdCard,
  LockKeyhole,
  ShieldCheck,
  Smartphone,
  UserPen,
  type LucideIcon,
} from 'lucide-react'
import { GlassCard } from '../ui/GlassCard'

interface Step {
  icon: LucideIcon
  title: string
  description: string
  live: boolean
}

const STEPS: Step[] = [
  { icon: UserPen, title: 'Create your account', description: 'Name, email, phone and a strong password.', live: true },
  { icon: Smartphone, title: 'Verify your phone', description: 'Enter the six-digit one-time code.', live: true },
  { icon: LockKeyhole, title: 'Sign in securely', description: 'JWT sessions with role-based access.', live: true },
  { icon: ClipboardCheck, title: 'Personal details', description: 'Date of birth, address, emergency contact.', live: false },
  { icon: IdCard, title: 'Identity & licence', description: 'National ID and driver licence.', live: false },
  { icon: CarFront, title: 'Vehicle details', description: 'Type, plate, make and model.', live: false },
  { icon: FileUp, title: 'Document uploads', description: 'Licence, registration and insurance.', live: false },
  { icon: ShieldCheck, title: 'Review & submit', description: 'Admin approves or rejects your application.', live: false },
]

export function JourneyTimeline() {
  return (
    <section aria-labelledby="journey-heading" className="py-12">
      <h2 id="journey-heading" className="text-center font-display text-2xl font-bold sm:text-3xl">
        Your journey, step by step
      </h2>
      <p className="mx-auto mt-3 max-w-2xl text-center text-muted">
        Phase 1 delivers account creation, phone verification and secure sign-in. The remaining steps are on the
        roadmap.
      </p>
      <ol className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map(({ icon: Icon, title, description, live }, index) => (
          <motion.li
            key={title}
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.45, delay: (index % 4) * 0.07 }}
          >
            <GlassCard className={`h-full p-5 ${live ? '' : 'opacity-90'}`}>
              <div className="flex items-center justify-between">
                <span
                  className={`flex h-11 w-11 items-center justify-center rounded-xl ${
                    live ? 'bg-brand text-brand-fg' : 'bg-brand-soft text-brand'
                  }`}
                >
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </span>
                <span className="text-sm font-semibold text-muted">
                  <span className="sr-only">Step </span>
                  {index + 1}
                </span>
              </div>
              <h3 className="mt-4 font-semibold text-fg">{title}</h3>
              <p className="mt-1 text-sm leading-6 text-muted">{description}</p>
              <span
                className={`mt-3 inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                  live ? 'bg-success-soft text-success' : 'bg-accent-soft text-accent'
                }`}
              >
                {live ? 'Live in Phase 1' : 'Coming soon'}
              </span>
            </GlassCard>
          </motion.li>
        ))}
      </ol>
    </section>
  )
}
