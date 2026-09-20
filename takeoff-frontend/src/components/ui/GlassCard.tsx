import type { HTMLAttributes } from 'react'

/** Frosted-glass surface. Opaque-enough backgrounds keep text contrast safe in both themes. */
export function GlassCard({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`glass rounded-3xl ${className}`} {...rest} />
}
