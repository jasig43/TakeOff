import { motion } from 'framer-motion'
import { BadgeCheck, Clock, MapPin, Package, Route, Truck, type LucideIcon } from 'lucide-react'

interface Badge {
  icon: LucideIcon
  label: string
  caption: string
  /** Tailwind position classes inside the stage. */
  position: string
  /** Gradient for the icon tile. */
  tile: string
  floatDistance: number
  duration: number
  delay: number
}

const BADGES: Badge[] = [
  {
    icon: Package,
    label: 'Packages',
    caption: 'Parcel ready',
    position: 'left-0 top-4 sm:left-2',
    tile: 'from-amber-300 to-orange-500',
    floatDistance: 12,
    duration: 5.2,
    delay: 0,
  },
  {
    icon: Route,
    label: 'Routes',
    caption: 'Route planned',
    position: 'right-0 top-0 sm:right-2',
    tile: 'from-sky-300 to-blue-600',
    floatDistance: 16,
    duration: 6.4,
    delay: 0.6,
  },
  {
    icon: Truck,
    label: 'Vehicles',
    caption: 'Van on the road',
    position: 'left-4 top-1/2 -translate-y-1/2 sm:left-10',
    tile: 'from-indigo-300 to-violet-600',
    floatDistance: 14,
    duration: 5.8,
    delay: 1.1,
  },
  {
    icon: MapPin,
    label: 'Delivery',
    caption: 'Drop-off point',
    position: 'right-2 top-1/2 translate-y-6 sm:right-8',
    tile: 'from-rose-300 to-red-500',
    floatDistance: 10,
    duration: 4.8,
    delay: 0.3,
  },
  {
    icon: BadgeCheck,
    label: 'Verified drivers',
    caption: 'Identity checked',
    position: 'bottom-2 left-0 sm:left-6',
    tile: 'from-emerald-300 to-green-600',
    floatDistance: 13,
    duration: 6,
    delay: 0.9,
  },
  {
    icon: Clock,
    label: 'On schedule',
    caption: 'ETA on track',
    position: 'bottom-0 right-0 sm:right-4',
    tile: 'from-cyan-300 to-teal-600',
    floatDistance: 11,
    duration: 5.5,
    delay: 1.5,
  },
]

/**
 * Decorative cluster of glass "3D" tiles floating over a dashed route. Everything here is
 * illustrative (aria-hidden) and labelled as such; it does not depict live data.
 */
export function FloatingLogisticsBadges() {
  return (
    <div className="relative mx-auto w-full max-w-md" style={{ perspective: '1100px' }}>
      <div aria-hidden="true" className="relative h-[24rem] sm:h-[28rem]">
        {/* Dashed route drawn behind the tiles. */}
        <svg viewBox="0 0 400 440" className="absolute inset-0 h-full w-full" fill="none">
          <motion.path
            d="M56 90 C 190 40, 330 120, 330 210 S 90 250, 96 330 S 250 410, 340 372"
            stroke="var(--brand)"
            strokeWidth="3"
            strokeLinecap="round"
            strokeDasharray="2 12"
            initial={{ pathLength: 0, opacity: 0 }}
            animate={{ pathLength: 1, opacity: 0.7 }}
            transition={{ duration: 2.4, ease: 'easeInOut', delay: 0.6 }}
          />
          <circle cx="56" cy="90" r="7" fill="var(--accent)" />
          <circle cx="340" cy="372" r="7" fill="var(--success)" />
        </svg>

        {BADGES.map(({ icon: Icon, label, caption, position, tile, floatDistance, duration, delay }) => (
          <motion.div
            key={label}
            className={`absolute ${position}`}
            initial={{ opacity: 0, scale: 0.85 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, delay: 0.5 + delay * 0.3 }}
          >
            <motion.div
              animate={{ y: [0, -floatDistance, 0], rotateZ: [-1.5, 1.5, -1.5] }}
              transition={{ duration, delay, repeat: Infinity, ease: 'easeInOut' }}
              whileHover={{ rotateX: -10, rotateY: 12, scale: 1.06 }}
              style={{ transformStyle: 'preserve-3d' }}
              className="glass flex items-center gap-3 rounded-2xl bg-surface-strong/70 py-2.5 pl-2.5 pr-4"
            >
              <span
                className={`flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br ${tile} text-white shadow-[0_10px_18px_-6px_rgb(0_0_0/0.5),inset_0_2px_3px_rgb(255_255_255/0.6),inset_0_-4px_6px_rgb(0_0_0/0.18)]`}
                style={{ transform: 'translateZ(24px)' }}
              >
                <Icon className="h-6 w-6 drop-shadow" />
              </span>
              <span className="text-left leading-tight">
                <span className="block text-sm font-semibold text-fg">{label}</span>
                <span className="block text-xs text-muted">{caption}</span>
              </span>
            </motion.div>
          </motion.div>
        ))}
      </div>
      <p className="mt-2 text-center text-xs text-muted">Illustration only. Not live data.</p>
    </div>
  )
}
