import { useRef, useState, type FormEvent } from 'react'
import { adminApi } from '../../api/authApi'
import { isApiError } from '../../api/client'
import type { IssuedCredential, Role } from '../../api/types'
import { errorMessage, fieldMessages } from '../../utils/apiHelpers'
import { ROLES, ROLE_LABEL } from '../../utils/formatting'
import { isValidEmail, isValidFullName, normalizePhone, phoneProblem } from '../../utils/formValidation'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { SelectField } from '../ui/SelectField'
import { TextField } from '../ui/TextField'

type Field = 'fullName' | 'email' | 'phoneNumber' | 'role'
type Errors = Partial<Record<Field, string>>

interface CreateAccountFormProps {
  onCreated: (credential: IssuedCredential) => void
  onCancel: () => void
}

/**
 * Creates an account for someone else. There is no password field on purpose: the server generates a temporary one and
 * returns it once (shown by TemporaryPasswordNotice), and the person must replace it at first sign-in.
 */
export function CreateAccountForm({ onCreated, onCancel }: CreateAccountFormProps) {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [role, setRole] = useState<Role | ''>('APPLICANT_DRIVER')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  const clear = (field: Field) => {
    setFormError('')
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  const validate = (): Errors => ({
    fullName: isValidFullName(fullName) ? undefined : 'Enter their full name (2 to 100 characters).',
    email: isValidEmail(email) ? undefined : 'Enter a valid email address, for example name@example.com.',
    phoneNumber: phoneProblem(phone),
    role: role ? undefined : 'Choose a role.',
  })

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current) return
    const found = validate()
    setErrors(found)
    setFormError('')
    if (Object.values(found).some(Boolean) || !role) return

    inFlight.current = true
    setSubmitting(true)
    try {
      onCreated(
        await adminApi.createUser({
          fullName: fullName.trim(),
          email: email.trim().toLowerCase(),
          phoneNumber: normalizePhone(phone),
          role,
        }),
      )
    } catch (error) {
      const fields = fieldMessages(error) as Errors
      if (isApiError(error) && error.code === 'EMAIL_ALREADY_REGISTERED') {
        setErrors({ email: error.message })
      } else if (isApiError(error) && error.code === 'PHONE_ALREADY_REGISTERED') {
        setErrors({ phoneNumber: error.message })
      } else if (Object.keys(fields).length > 0) {
        setErrors(fields)
      } else {
        setFormError(errorMessage(error, 'We could not create the account. Please try again.'))
      }
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <GlassCard className="p-6">
      <h3 className="text-lg font-semibold">Create an account</h3>
      <p className="mt-1 text-sm text-muted">
        We generate a temporary password and show it to you once. The person signs in with it and is then asked to choose
        their own.
      </p>

      <form onSubmit={handleSubmit} noValidate aria-label="New account" className="mt-5 space-y-5">
        {formError && (
          <p role="alert" className="rounded-xl border border-danger bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
            {formError}
          </p>
        )}
        <div className="grid gap-5 sm:grid-cols-2">
          <TextField
            label="Full name"
            required
            autoComplete="off"
            value={fullName}
            onChange={(e) => {
              setFullName(e.target.value)
              clear('fullName')
            }}
            error={errors.fullName}
            disabled={submitting}
          />
          <TextField
            label="Email address"
            type="email"
            required
            autoComplete="off"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              clear('email')
            }}
            error={errors.email}
            disabled={submitting}
          />
          <TextField
            label="Phone number"
            type="tel"
            required
            autoComplete="off"
            placeholder="+263771234567"
            hint="International format with the country code."
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value)
              clear('phoneNumber')
            }}
            error={errors.phoneNumber}
            disabled={submitting}
          />
          <SelectField
            label="Role"
            required
            value={role}
            onChange={(e) => {
              setRole(e.target.value as Role | '')
              clear('role')
            }}
            error={errors.role}
            disabled={submitting}
          >
            <option value="">Choose a role</option>
            {ROLES.map((value) => (
              <option key={value} value={value}>
                {ROLE_LABEL[value]}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button type="submit" loading={submitting} loadingLabel="Creating account">
            Create account
          </Button>
          <Button variant="ghost" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
        </div>
      </form>
    </GlassCard>
  )
}
