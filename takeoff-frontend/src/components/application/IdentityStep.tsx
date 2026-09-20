import { applicationApi } from '../../api/applicationApi'
import type { Application } from '../../api/types'
import { useStepForm } from '../../hooks/useStepForm'
import {
  todayIso,
  validateLicenceClass,
  validateLicenceExpiry,
  validateLicenceNumber,
  validateNationalId,
} from '../../utils/applicationValidation'
import { TextField } from '../ui/TextField'
import { StepShell } from './StepShell'

type Field = 'nationalId' | 'licenceNumber' | 'licenceClass' | 'licenceExpiry'

interface StepProps {
  application: Application
  onSaved: (application: Application) => void
  onBack?: () => void
}

export function IdentityStep({ application, onSaved, onBack }: StepProps) {
  const { identity } = application
  const form = useStepForm<Record<Field, string>, Field>({
    initial: {
      nationalId: identity.nationalId ?? '',
      licenceNumber: identity.licenceNumber ?? '',
      licenceClass: identity.licenceClass ?? '',
      licenceExpiry: identity.licenceExpiry ?? '',
    },
    validate: (v) => ({
      nationalId: validateNationalId(v.nationalId),
      licenceNumber: validateLicenceNumber(v.licenceNumber),
      licenceClass: validateLicenceClass(v.licenceClass),
      licenceExpiry: validateLicenceExpiry(v.licenceExpiry),
    }),
    save: (v) =>
      applicationApi.saveIdentity({
        nationalId: v.nationalId.trim(),
        licenceNumber: v.licenceNumber.trim(),
        licenceClass: v.licenceClass.trim(),
        licenceExpiry: v.licenceExpiry,
      }),
    onSaved,
    fallbackError: 'We could not save your identity details. Please try again.',
    codeFields: { NATIONAL_ID_IN_USE: 'nationalId' },
  })
  const { values, errors, set } = form

  return (
    <StepShell
      title="Identity and driver's licence"
      description="We use these to confirm who you are and that you are licensed to drive."
      saving={form.saving}
      formError={form.formError}
      onSubmit={() => void form.submit()}
      onBack={onBack}
    >
      <div className="sm:col-span-2">
        <TextField
          label="National ID number"
          required
          autoComplete="off"
          value={values.nationalId}
          onChange={(e) => set('nationalId', e.target.value)}
          error={errors.nationalId}
        />
      </div>
      <TextField
        label="Driver's licence number"
        required
        autoComplete="off"
        value={values.licenceNumber}
        onChange={(e) => set('licenceNumber', e.target.value)}
        error={errors.licenceNumber}
      />
      <TextField
        label="Licence class"
        required
        placeholder="4"
        autoComplete="off"
        value={values.licenceClass}
        onChange={(e) => set('licenceClass', e.target.value)}
        error={errors.licenceClass}
      />
      <TextField
        label="Licence expiry date"
        type="date"
        required
        min={todayIso()}
        value={values.licenceExpiry}
        onChange={(e) => set('licenceExpiry', e.target.value)}
        error={errors.licenceExpiry}
      />
    </StepShell>
  )
}
