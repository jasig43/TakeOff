import { motion } from 'framer-motion'

/**
 * Soft gradient-mesh backdrop with slowly drifting blobs. Purely decorative (aria-hidden) and
 * pointer-events-none so it can never block interaction. <MotionConfig reducedMotion="user">
 * at the app root freezes the drift for users who prefer reduced motion.
 */
export function MeshBackground() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <motion.div
        className="absolute -left-40 -top-40 h-[34rem] w-[34rem] rounded-full blur-3xl"
        style={{ background: 'var(--mesh-1)' }}
        animate={{ x: [0, 80, 0], y: [0, 50, 0] }}
        transition={{ duration: 24, repeat: Infinity, ease: 'easeInOut' }}
      />
      <motion.div
        className="absolute -right-32 top-1/4 h-[28rem] w-[28rem] rounded-full blur-3xl"
        style={{ background: 'var(--mesh-2)' }}
        animate={{ x: [0, -70, 0], y: [0, 60, 0] }}
        transition={{ duration: 28, repeat: Infinity, ease: 'easeInOut' }}
      />
      <motion.div
        className="absolute -bottom-40 left-1/3 h-[30rem] w-[30rem] rounded-full blur-3xl"
        style={{ background: 'var(--mesh-3)' }}
        animate={{ x: [0, 60, 0], y: [0, -50, 0] }}
        transition={{ duration: 26, repeat: Infinity, ease: 'easeInOut' }}
      />
      {/* Fine grid gives the glass surfaces something to refract without adding noise. */}
      <div
        className="absolute inset-0 opacity-[0.35] dark:opacity-[0.25]"
        style={{
          backgroundImage:
            'linear-gradient(var(--line) 1px, transparent 1px), linear-gradient(90deg, var(--line) 1px, transparent 1px)',
          backgroundSize: '56px 56px',
          maskImage: 'radial-gradient(ellipse at 50% 30%, black 20%, transparent 70%)',
          WebkitMaskImage: 'radial-gradient(ellipse at 50% 30%, black 20%, transparent 70%)',
        }}
      />
    </div>
  )
}
