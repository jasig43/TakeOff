import { motion, type Variants } from 'framer-motion'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ButtonLink } from '../ui/Button'
import { FloatingLogisticsBadges } from './FloatingLogisticsBadges'

const container: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.09, delayChildren: 0.1 } },
}

const rise: Variants = {
  hidden: { opacity: 0, y: 26 },
  show: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } },
}

const HEADLINE = ['Onboard', 'verified', 'drivers,', 'faster.']

export function HeroSection() {
  return (
    <section aria-labelledby="hero-heading" className="grid items-center gap-12 py-8 lg:grid-cols-2 lg:py-16">
      <motion.div variants={container} initial="hidden" animate="show" className="text-center lg:text-left">
        <motion.p
          variants={rise}
          className="glass mx-auto inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium text-fg lg:mx-0"
        >
          <ShieldCheck className="h-4 w-4 text-success" aria-hidden="true" />
          Driver onboarding for courier &amp; logistics teams
        </motion.p>

        <h1
          id="hero-heading"
          className="mt-6 font-display text-4xl font-extrabold leading-[1.08] tracking-tight sm:text-5xl lg:text-6xl"
        >
          {HEADLINE.map((word, index) => (
            <motion.span
              key={word}
              variants={rise}
              className={`inline-block pr-[0.25em] ${index === 1 ? 'bg-gradient-to-r from-brand to-accent bg-clip-text text-transparent' : ''}`}
            >
              {word}
            </motion.span>
          ))}
        </h1>

        <motion.p variants={rise} className="mx-auto mt-6 max-w-xl text-lg leading-8 text-muted lg:mx-0">
          <span className="font-semibold text-fg">TakeOFF</span> takes drivers from sign-up to a reviewed application
          in one guided flow: secure registration, phone verification, and role-based access for your logistics
          administrators.
        </motion.p>

        <motion.div
          variants={rise}
          className="mt-9 flex flex-col items-stretch gap-3 sm:flex-row sm:justify-center lg:justify-start"
        >
          <ButtonLink to="/register" size="lg" pulse>
            Become a Driver
            <ArrowRight className="h-5 w-5" aria-hidden="true" />
          </ButtonLink>
          <ButtonLink to="/admin/login" size="lg" variant="secondary">
            Admin Portal
          </ButtonLink>
        </motion.div>

        <motion.p variants={rise} className="mt-5 text-sm text-muted">
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-brand underline-offset-4 hover:underline">
            Sign in
          </Link>
        </motion.p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, x: 30 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.8, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
      >
        <FloatingLogisticsBadges />
      </motion.div>
    </section>
  )
}
