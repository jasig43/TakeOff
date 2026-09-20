import { ArrowLeft, ArrowRight } from 'lucide-react'
import { applicationApi } from '../../api/applicationApi'
import type { Application, DocumentType } from '../../api/types'
import { useToast } from '../../hooks/useToast'
import { errorMessage, fieldMessages, openProtectedFile } from '../../utils/apiHelpers'
import { DOCUMENT_TYPES } from '../../utils/formatting'
import { Button } from '../ui/Button'
import { FileUploadCard } from './FileUploadCard'

interface DocumentsStepProps {
  application: Application
  /** Called with the fresh application after every upload or removal. */
  onChanged: (application: Application) => void
  onBack: () => void
  onNext: () => void
}

/** Failures are rethrown as plain Errors carrying the message to show under the card. */
function failure(error: unknown, fallback: string): Error {
  const fields = Object.values(fieldMessages(error))
  return new Error(fields.length > 0 ? fields.join(' ') : errorMessage(error, fallback))
}

export function DocumentsStep({ application, onChanged, onBack, onNext }: DocumentsStepProps) {
  const toast = useToast()
  const allUploaded = DOCUMENT_TYPES.every((type) => application.documents.some((d) => d.type === type))

  const upload = async (type: DocumentType, file: File) => {
    try {
      onChanged(await applicationApi.uploadDocument(type, file))
      toast.success('Document uploaded.')
    } catch (error) {
      throw failure(error, 'The upload failed. Please try again.')
    }
  }

  const remove = async (type: DocumentType) => {
    try {
      onChanged(await applicationApi.deleteDocument(type))
    } catch (error) {
      throw failure(error, 'The document could not be removed. Please try again.')
    }
  }

  const view = async (type: DocumentType) => {
    try {
      await openProtectedFile(() => applicationApi.documentBlob(type))
    } catch (error) {
      throw failure(error, 'The document could not be opened.')
    }
  }

  return (
    <section aria-labelledby="documents-heading" className="space-y-4">
      <div>
        <h2 id="documents-heading" className="text-xl font-semibold">
          Documents
        </h2>
        <p className="mt-1 text-sm text-muted">
          Upload a clear copy of each document. Files are stored securely and only you and our review team can open them.
        </p>
      </div>

      {DOCUMENT_TYPES.map((type) => (
        <FileUploadCard
          key={type}
          type={type}
          document={application.documents.find((d) => d.type === type)}
          onUpload={upload}
          onRemove={remove}
          onView={view}
        />
      ))}

      <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:items-center sm:justify-between">
        <Button variant="secondary" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back
        </Button>
        <div className="flex flex-col gap-2 sm:items-end">
          {!allUploaded && <p className="text-sm text-muted">Upload all three documents to continue.</p>}
          <Button onClick={onNext} disabled={!allUploaded}>
            Continue to review
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </section>
  )
}
