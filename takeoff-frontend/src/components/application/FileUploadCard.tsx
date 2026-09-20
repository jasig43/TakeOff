import { useId, useState, type ChangeEvent } from 'react'
import { CircleAlert, Eye, FileText, LoaderCircle, Trash2, Upload } from 'lucide-react'
import type { DocumentInfo, DocumentType } from '../../api/types'
import { ACCEPT_ATTRIBUTE, MAX_UPLOAD_BYTES, validateDocumentFile } from '../../utils/applicationValidation'
import { DOCUMENT_LABEL, formatDateTime, formatFileSize } from '../../utils/formatting'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'

interface FileUploadCardProps {
  type: DocumentType
  document: DocumentInfo | undefined
  /** Uploads (or replaces) the file. Rejects with a user-safe message. */
  onUpload: (type: DocumentType, file: File) => Promise<void>
  onRemove: (type: DocumentType) => Promise<void>
  onView: (type: DocumentType) => Promise<void>
}

/** One required document: shows what is uploaded, and lets the driver upload, replace, view or remove it. */
export function FileUploadCard({ type, document, onUpload, onRemove, onView }: FileUploadCardProps) {
  const inputId = useId()
  const label = DOCUMENT_LABEL[type]
  const [busy, setBusy] = useState<'upload' | 'remove' | 'view' | null>(null)
  const [error, setError] = useState('')

  const run = async (kind: 'upload' | 'remove' | 'view', action: () => Promise<void>) => {
    setBusy(kind)
    setError('')
    try {
      await action()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(null)
    }
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.target
    const file = input.files?.[0]
    // Reset so choosing the same file again (after fixing it) still fires a change event.
    input.value = ''
    if (!file) return
    const problem = validateDocumentFile(file)
    if (problem) {
      setError(problem)
      return
    }
    void run('upload', () => onUpload(type, file))
  }

  const uploading = busy === 'upload'

  return (
    <GlassCard className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand">
            <FileText className="h-5 w-5" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 className="font-semibold">{label}</h3>
            {document ? (
              <p className="mt-0.5 break-words text-sm text-muted">
                {document.filename} ({formatFileSize(document.sizeBytes)}), uploaded {formatDateTime(document.uploadedAt)}
              </p>
            ) : (
              <p className="mt-0.5 text-sm text-muted">
                PDF, JPG or PNG, up to {MAX_UPLOAD_BYTES / (1024 * 1024)} MB.
              </p>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {document && (
            <>
              <Button
                variant="secondary"
                onClick={() => void run('view', () => onView(type))}
                disabled={busy !== null}
                aria-label={`View ${label}`}
              >
                {busy === 'view' ? (
                  <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                ) : (
                  <Eye className="h-4 w-4" aria-hidden="true" />
                )}
                View
              </Button>
              <Button
                variant="ghost"
                onClick={() => void run('remove', () => onRemove(type))}
                disabled={busy !== null}
                aria-label={`Remove ${label}`}
              >
                {busy === 'remove' ? (
                  <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                ) : (
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                )}
                Remove
              </Button>
            </>
          )}

          {/* The native input stays in the DOM (visually hidden) so it is keyboard- and screen-reader-operable. */}
          <input
            id={inputId}
            type="file"
            accept={ACCEPT_ATTRIBUTE}
            className="peer sr-only"
            onChange={handleChange}
            disabled={busy !== null}
            aria-label={`${document ? 'Replace' : 'Upload'} ${label}`}
          />
          <label
            htmlFor={inputId}
            aria-hidden="true"
            className={`glass inline-flex min-h-11 cursor-pointer select-none items-center justify-center gap-2 rounded-2xl px-5 py-2.5 text-sm font-semibold text-fg transition hover:bg-brand-soft peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand peer-disabled:cursor-not-allowed peer-disabled:opacity-55`}
          >
            {uploading ? (
              <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Upload className="h-4 w-4" aria-hidden="true" />
            )}
            {uploading ? 'Uploading' : document ? 'Replace' : 'Choose file'}
          </label>
        </div>
      </div>

      {uploading && (
        <p role="status" className="sr-only">
          Uploading {label}
        </p>
      )}
      {error && (
        <p role="alert" className="mt-3 flex items-start gap-1.5 text-sm font-medium text-danger">
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      )}
    </GlassCard>
  )
}
