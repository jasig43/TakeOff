import { useState } from 'react'
import { isApiError } from '../api/client'
import type { Application } from '../api/types'
import { errorMessage, fieldMessages } from '../utils/apiHelpers'

type Errors<K extends string> = Partial<Record<K, string>>

/**
 * State and submit handling shared by the three data-entry steps.
 * `validate` returns per-field messages (empty when valid); `save` calls the API. Server-side field errors are mapped
 * back onto the same fields, so the backend stays authoritative when it disagrees with the client checks.
 */
export function useStepForm<V extends object, K extends Extract<keyof V, string>>(options: {
  initial: V
  validate: (values: V) => Errors<K>
  save: (values: V) => Promise<Application>
  onSaved: (application: Application) => void
  fallbackError: string
  /** Server error codes that belong to one field (e.g. a duplicate ID), so they show under that field. */
  codeFields?: Partial<Record<string, K>>
}) {
  const { initial, validate, save, onSaved, fallbackError, codeFields } = options
  const [values, setValues] = useState<V>(initial)
  const [errors, setErrors] = useState<Errors<K>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = <F extends K>(field: F, value: V[F]) => {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  const submit = async () => {
    const found = validate(values)
    setErrors(found)
    setFormError('')
    if (Object.values(found).some(Boolean)) return

    setSaving(true)
    try {
      onSaved(await save(values))
    } catch (error) {
      const fromServer = fieldMessages(error) as Errors<K>
      const codeField = isApiError(error) ? codeFields?.[error.code] : undefined
      if (Object.keys(fromServer).length > 0) {
        setErrors(fromServer)
      } else if (codeField && isApiError(error)) {
        setErrors({ [codeField]: error.message } as Errors<K>)
      } else {
        setFormError(errorMessage(error, fallbackError))
      }
    } finally {
      setSaving(false)
    }
  }

  return { values, errors, formError, saving, set, submit }
}
