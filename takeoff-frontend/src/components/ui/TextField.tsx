import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { CircleAlert } from 'lucide-react'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string
  /** Error text; when present the field is flagged invalid and the message is linked via aria-describedby. */
  error?: string
  /** Helper text shown below the input. */
  hint?: ReactNode
  /** Element rendered inside the right edge of the input (e.g. show/hide password toggle). */
  trailing?: ReactNode
  /** Additional element ids to append to aria-describedby (e.g. a password checklist). */
  describedBy?: string
}

export function TextField({ label, error, hint, trailing, describedBy, className = '', ...inputProps }: TextFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedIds = [hint ? hintId : '', error ? errorId : '', describedBy ?? ''].filter(Boolean).join(' ')

  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-fg">
        {label}
        {inputProps.required && (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        )}
      </label>
      <div className="relative">
        <input
          id={id}
          className={`field ${trailing ? 'pr-12' : ''} ${className}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedIds || undefined}
          {...inputProps}
        />
        {trailing && <div className="absolute inset-y-0 right-1 flex items-center">{trailing}</div>}
      </div>
      {hint && (
        <p id={hintId} className="text-sm text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-sm font-medium text-danger">
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}
