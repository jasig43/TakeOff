import { applicationApi } from '../../api/applicationApi'
import type { Application } from '../../api/types'
import { useStepForm } from '../../hooks/useStepForm'
import {
  todayIso,
  validateDateOfBirth,
  validateEmergencyPhone,
  validateRequired,
} from '../../utils/applicationValidation'
import { TextField } from '../ui/TextField'
import { StepShell } from './StepShell'

type Field = 'dateOfBirth' | 'addressLine' | 'city' | 'emergencyContactName' | 'emergencyContactPhone'

interface StepProps {
  application: Application
  onSaved: (application: Application) => void
  onBack?: () => void
}

export function PersonalStep({ application, onSaved, onBack }: StepProps) {
  const { personal } = application
  const form = useStepForm<Record<Field, string>, Field>({
    initial: {
      dateOfBirth: personal.dateOfBirth ?? '',
      addressLine: personal.addressLine ?? '',
      city: personal.city ?? '',
      emergencyContactName: personal.emergencyContactName ?? '',
      emergencyContactPhone: personal.emergencyContactPhone ?? '',
    },
    validate: (v) => ({
      dateOfBirth: validateDateOfBirth(v.dateOfBirth),
      addressLine: validateRequired(v.addressLine, 'Address', 200),
      city: validateRequired(v.city, 'City or town', 100),
      emergencyContactName: validateRequired(v.emergencyContactName, 'Emergency contact name', 120),
      emergencyContactPhone: validateEmergencyPhone(v.emergencyContactPhone),
    }),
    save: (v) =>
      applicationApi.savePersonal({
        dateOfBirth: v.dateOfBirth,
        addressLine: v.addressLine.trim(),
        city: v.city.trim(),
        emergencyContactName: v.emergencyContactName.trim(),
        emergencyContactPhone: v.emergencyContactPhone.trim(),
      }),
    onSaved,
    fallbackError: 'We could not save your personal details. Please try again.',
  })
  const { values, errors, set } = form

  return (
    <StepShell
      title="Personal details"
      description="Tell us about yourself and who we can call in an emergency."
      saving={form.saving}
      formError={form.formError}
      onSubmit={() => void form.submit()}
      onBack={onBack}
    >
      <TextField
        label="Date of birth"
        type="date"
        required
        max={todayIso()}
        autoComplete="bday"
        value={values.dateOfBirth}
        onChange={(e) => set('dateOfBirth', e.target.value)}
        error={errors.dateOfBirth}
      />
      <TextField
        label="City or town"
        required
        autoComplete="address-level2"
        value={values.city}
        onChange={(e) => set('city', e.target.value)}
        error={errors.city}
      />
      <div className="sm:col-span-2">
        <TextField
          label="Address"
          required
          autoComplete="street-address"
          value={values.addressLine}
          onChange={(e) => set('addressLine', e.target.value)}
          error={errors.addressLine}
        />
      </div>
      <TextField
        label="Emergency contact name"
        required
        value={values.emergencyContactName}
        onChange={(e) => set('emergencyContactName', e.target.value)}
        error={errors.emergencyContactName}
      />
      <TextField
        label="Emergency contact phone"
        type="tel"
        required
        placeholder="+263771234567"
        value={values.emergencyContactPhone}
        onChange={(e) => set('emergencyContactPhone', e.target.value)}
        error={errors.emergencyContactPhone}
      />
    </StepShell>
  )
}
