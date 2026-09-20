import { Check } from 'lucide-react'

export interface StepInfo {
  id: string
  label: string
  complete: boolean
}

interface StepIndicatorProps {
  steps: StepInfo[]
  current: number
  onSelect: (index: number) => void
}

/** Horizontal step list. Steps are buttons so the wizard is fully keyboard-operable; completion is icon + text. */
export function StepIndicator({ steps, current, onSelect }: StepIndicatorProps) {
  return (
    <nav aria-label="Application steps">
      <ol className="grid grid-cols-2 gap-2 sm:grid-cols-5">
        {steps.map((step, index) => {
          const active = index === current
          return (
            <li key={step.id}>
              <button
                type="button"
                onClick={() => onSelect(index)}
                aria-current={active ? 'step' : undefined}
                className={`flex min-h-12 w-full items-center gap-2 rounded-2xl border px-3 py-2 text-left text-sm font-medium transition ${
                  active ? 'border-brand bg-brand-soft text-brand' : 'border-line text-fg hover:bg-brand-soft'
                }`}
              >
                <span
                  className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
                    step.complete ? 'bg-success text-brand-fg' : active ? 'bg-brand text-brand-fg' : 'bg-line text-fg'
                  }`}
                  aria-hidden="true"
                >
                  {step.complete ? <Check className="h-3.5 w-3.5" strokeWidth={3} /> : index + 1}
                </span>
                <span>
                  {step.label}
                  <span className="sr-only">{step.complete ? ' (complete)' : ' (not complete)'}</span>
                </span>
              </button>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
