import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import { LoaderCircle } from 'lucide-react'
import { buttonClasses, type ButtonSize, type ButtonVariant } from './buttonStyles'

interface StyleProps {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
  pulse?: boolean
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, StyleProps {
  loading?: boolean
  /** Text announced to assistive tech while loading (defaults to "Loading"). */
  loadingLabel?: string
}

export function Button({
  variant,
  size,
  fullWidth,
  pulse,
  loading = false,
  loadingLabel = 'Loading',
  className,
  disabled,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClasses({ variant, size, fullWidth, pulse: pulse && !loading && !disabled, className })}
      // Disabled while loading so repeated clicks can't submit twice.
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && (
        <>
          <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          <span className="sr-only">{loadingLabel}</span>
        </>
      )}
      {children}
    </button>
  )
}

interface ButtonLinkProps extends LinkProps, StyleProps {
  children: ReactNode
}

export function ButtonLink({ variant, size, fullWidth, pulse, className, children, ...rest }: ButtonLinkProps) {
  return (
    <Link className={buttonClasses({ variant, size, fullWidth, pulse, className })} {...rest}>
      {children}
    </Link>
  )
}
