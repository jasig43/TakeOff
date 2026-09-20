import { useState, type ReactNode } from 'react'
import { Eye, LoaderCircle } from 'lucide-react'
import type { Application, DocumentType } from '../../api/types'
import { errorMessage, openProtectedFile } from '../../utils/apiHelpers'
import {
  DOCUMENT_LABEL,
  DOCUMENT_TYPES,
  VEHICLE_LABEL,
  formatDate,
  formatDateTime,
  formatFileSize,
} from '../../utils/formatting'
import { useToast } from '../../hooks/useToast'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[12rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="text-sm font-medium text-fg">{children || <span className="text-muted">Not provided</span>}</dd>
    </div>
  )
}

interface SectionProps {
  title: string
  /** Renders an "Edit" affordance (drivers, while the application is editable). */
  action?: ReactNode
  children: ReactNode
}

function Section({ title, action, children }: SectionProps) {
  return (
    <GlassCard className="p-6">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-lg font-semibold">{title}</h3>
        {action}
      </div>
      <dl className="mt-4 space-y-3">{children}</dl>
    </GlassCard>
  )
}

interface ApplicationSummaryProps {
  application: Application
  /** Loads a document's bytes (the driver and admin endpoints differ). */
  loadDocument: (type: DocumentType) => Promise<Blob>
  /** When given, each section shows an Edit button that calls it with the step index. */
  onEdit?: (stepIndex: number) => void
}

/** Read-only view of everything in an application. Used by the driver's review step and the admin's detail page. */
export function ApplicationSummary({ application, loadDocument, onEdit }: ApplicationSummaryProps) {
  const toast = useToast()
  const [opening, setOpening] = useState<DocumentType | null>(null)
  const { personal, identity, vehicle } = application

  const edit = (index: number, label: string) =>
    onEdit && (
      <Button variant="ghost" onClick={() => onEdit(index)} aria-label={`Edit ${label}`}>
        Edit
      </Button>
    )

  const view = async (type: DocumentType) => {
    setOpening(type)
    try {
      await openProtectedFile(() => loadDocument(type))
    } catch (error) {
      toast.error(errorMessage(error, 'The document could not be opened.'))
    } finally {
      setOpening(null)
    }
  }

  return (
    <div className="space-y-4">
      <Section title="Personal details" action={edit(0, 'personal details')}>
        <Row label="Date of birth">{personal.dateOfBirth ? formatDate(personal.dateOfBirth) : ''}</Row>
        <Row label="Address">{personal.addressLine}</Row>
        <Row label="City or town">{personal.city}</Row>
        <Row label="Emergency contact">{personal.emergencyContactName}</Row>
        <Row label="Emergency phone">{personal.emergencyContactPhone}</Row>
      </Section>

      <Section title="Identity and licence" action={edit(1, 'identity and licence')}>
        <Row label="National ID">{identity.nationalId}</Row>
        <Row label="Licence number">{identity.licenceNumber}</Row>
        <Row label="Licence class">{identity.licenceClass}</Row>
        <Row label="Licence expiry">{identity.licenceExpiry ? formatDate(identity.licenceExpiry) : ''}</Row>
      </Section>

      <Section title="Vehicle" action={edit(2, 'vehicle')}>
        <Row label="Type">{vehicle.vehicleType ? VEHICLE_LABEL[vehicle.vehicleType] : ''}</Row>
        <Row label="Registration number">{vehicle.plateNumber}</Row>
        <Row label="Make">{vehicle.make}</Row>
        <Row label="Model">{vehicle.model}</Row>
      </Section>

      <Section title="Documents" action={edit(3, 'documents')}>
        {DOCUMENT_TYPES.map((type) => {
          const document = application.documents.find((d) => d.type === type)
          return (
            <Row key={type} label={DOCUMENT_LABEL[type]}>
              {document && (
                <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>
                    {document.filename}{' '}
                    <span className="font-normal text-muted">
                      ({formatFileSize(document.sizeBytes)}, uploaded {formatDateTime(document.uploadedAt)})
                    </span>
                  </span>
                  <Button
                    variant="secondary"
                    onClick={() => void view(type)}
                    disabled={opening === type}
                    aria-label={`View ${DOCUMENT_LABEL[type]}`}
                  >
                    {opening === type ? (
                      <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                    ) : (
                      <Eye className="h-4 w-4" aria-hidden="true" />
                    )}
                    View
                  </Button>
                </span>
              )}
            </Row>
          )
        })}
      </Section>
    </div>
  )
}
