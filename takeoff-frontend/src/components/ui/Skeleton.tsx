/** Shimmering placeholder. Purely decorative: pair it with a labelled busy region. */
export function Skeleton({ className = '' }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`relative overflow-hidden rounded-xl bg-brand-soft before:absolute before:inset-0 before:-translate-x-full before:animate-shimmer before:bg-gradient-to-r before:from-transparent before:via-white/20 before:to-transparent ${className}`}
    />
  )
}
