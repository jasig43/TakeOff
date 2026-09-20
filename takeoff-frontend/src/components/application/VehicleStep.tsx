import { applicationApi } from '../../api/applicationApi'
import type { Application, VehicleType } from '../../api/types'
import { useStepForm } from '../../hooks/useStepForm'
import { validatePlate, validateRequired } from '../../utils/applicationValidation'
import { VEHICLE_LABEL, VEHICLE_TYPES } from '../../utils/formatting'
import { SelectField } from '../ui/SelectField'
import { TextField } from '../ui/TextField'
import { StepShell } from './StepShell'

type Field = 'vehicleType' | 'plateNumber' | 'make' | 'model'

interface StepProps {
  application: Application
  onSaved: (application: Application) => void
  onBack?: () => void
}

export function VehicleStep({ application, onSaved, onBack }: StepProps) {
  const { vehicle } = application
  const form = useStepForm<Record<Field, string>, Field>({
    initial: {
      vehicleType: vehicle.vehicleType ?? '',
      plateNumber: vehicle.plateNumber ?? '',
      make: vehicle.make ?? '',
      model: vehicle.model ?? '',
    },
    validate: (v) => ({
      vehicleType: v.vehicleType ? undefined : 'Choose a vehicle type.',
      plateNumber: validatePlate(v.plateNumber),
      make: validateRequired(v.make, 'Make', 60),
      model: validateRequired(v.model, 'Model', 60),
    }),
    save: (v) =>
      applicationApi.saveVehicle({
        vehicleType: v.vehicleType as VehicleType,
        plateNumber: v.plateNumber.trim().toUpperCase(),
        make: v.make.trim(),
        model: v.model.trim(),
      }),
    onSaved,
    fallbackError: 'We could not save your vehicle details. Please try again.',
    codeFields: { PLATE_IN_USE: 'plateNumber' },
  })
  const { values, errors, set } = form

  return (
    <StepShell
      title="Vehicle registration"
      description="The vehicle you will use for deliveries."
      saving={form.saving}
      formError={form.formError}
      onSubmit={() => void form.submit()}
      onBack={onBack}
    >
      <SelectField
        label="Vehicle type"
        required
        value={values.vehicleType}
        onChange={(e) => set('vehicleType', e.target.value)}
        error={errors.vehicleType}
      >
        <option value="">Select a type</option>
        {VEHICLE_TYPES.map((type) => (
          <option key={type} value={type}>
            {VEHICLE_LABEL[type]}
          </option>
        ))}
      </SelectField>
      <TextField
        label="Registration (plate) number"
        required
        autoComplete="off"
        placeholder="ABC 1234"
        value={values.plateNumber}
        onChange={(e) => set('plateNumber', e.target.value)}
        error={errors.plateNumber}
      />
      <TextField
        label="Make"
        required
        placeholder="Toyota"
        value={values.make}
        onChange={(e) => set('make', e.target.value)}
        error={errors.make}
      />
      <TextField
        label="Model"
        required
        placeholder="Hilux"
        value={values.model}
        onChange={(e) => set('model', e.target.value)}
        error={errors.model}
      />
    </StepShell>
  )
}
