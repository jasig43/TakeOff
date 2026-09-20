export type ButtonVariant = 'primary' | 'secondary' | 'ghost'
export type ButtonSize = 'md' | 'lg'

interface ButtonStyleOptions {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
  /** Adds the gentle attention-drawing pulse used on hero CTAs. */
  pulse?: boolean
  className?: string
}

const base =
  'relative inline-flex items-center justify-center gap-2 rounded-2xl font-semibold select-none ' +
  'transition duration-150 ease-out active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-55 disabled:active:scale-100'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-brand-fg shadow-lg shadow-brand/25 hover:brightness-110 disabled:shadow-none',
  secondary: 'glass text-fg hover:bg-brand-soft',
  ghost: 'text-fg hover:bg-brand-soft',
}

const sizes: Record<ButtonSize, string> = {
  md: 'min-h-11 px-5 py-2.5 text-sm',
  lg: 'min-h-12 px-7 py-3.5 text-base',
}

export function buttonClasses({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  pulse = false,
  className = '',
}: ButtonStyleOptions = {}): string {
  return [base, variants[variant], sizes[size], fullWidth ? 'w-full' : '', pulse ? 'animate-cta-pulse' : '', className]
    .filter(Boolean)
    .join(' ')
}
