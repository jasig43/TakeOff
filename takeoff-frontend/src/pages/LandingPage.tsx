import { ArrowRight } from 'lucide-react'
import { HeroSection } from '../components/landing/HeroSection'
import { JourneyTimeline } from '../components/landing/JourneyTimeline'
import { StatsTicker } from '../components/landing/StatsTicker'
import { PageShell } from '../components/layout/PageShell'
import { ButtonLink } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'

export default function LandingPage() {
  return (
    <PageShell>
      <HeroSection />
      <StatsTicker />
      <JourneyTimeline />
      <section aria-labelledby="cta-heading" className="py-12">
        <GlassCard className="px-6 py-12 text-center sm:px-12">
          <h2 id="cta-heading" className="font-display text-3xl font-bold">
            Ready to take off?
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-muted">
            Create your driver account in under two minutes, or sign in to the admin portal to manage applications.
          </p>
          <div className="mt-8 flex flex-col items-stretch justify-center gap-3 sm:flex-row">
            <ButtonLink to="/register" size="lg">
              Become a Driver
              <ArrowRight className="h-5 w-5" aria-hidden="true" />
            </ButtonLink>
            <ButtonLink to="/admin/login" size="lg" variant="secondary">
              Admin Portal
            </ButtonLink>
          </div>
        </GlassCard>
      </section>
    </PageShell>
  )
}
