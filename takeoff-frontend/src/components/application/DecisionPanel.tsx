import { useState } from 'react'
import { CircleCheck, CircleX } from 'lucide-react'
import { reviewApi } from '../../api/applicationApi'
import { isApiError } from '../../api/client'
import type { ApplicationDetail } from '../../api/types'
import { errorMessage } from '../../utils/apiHelpers'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { TextAreaField } from '../ui/TextAreaField'

const NOTE_MAX = 500

interface DecisionPanelProps {
  applicationId: number
  onDecided: (detail: ApplicationDetail) => void
  /** Called when the server says the application changed underneath us, so the page can reload it. */
  onConflict: () => void
}

/** Approve / reject controls for a pending application. Rejecting needs a reason; both ask for confirmation first. */
export function DecisionPanel({ applicationId, onDecided, onConflict }: DecisionPanelProps) {
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState('')
  const [choice, setChoice] = useState<'APPROVED' | 'REJECTED' | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const choose = (next: 'APPROVED' | 'REJECTED') => {
    setError('')
    if (next === 'REJECTED' && !note.trim()) {
      setNoteError('Explain why the application is not approved. The driver will see this note.')
      setChoice(null)
      return
    }
    setNoteError('')
    setChoice(next)
  }

  const confirm = async () => {
    if (!choice) return
    setSaving(true)
    setError('')
    try {
      onDecided(await reviewApi.decide(applicationId, { status: choice, note: note.trim() || undefined }))
    } catch (failure) {
      setError(errorMessage(failure, 'We could not save the decision. Please try again.'))
      setChoice(null)
      if (isApiError(failure) && (failure.code === 'INVALID_STATUS_TRANSITION' || failure.code === 'CONCURRENT_MODIFICATION')) {
        onConflict()
      }
    } finally {
      setSaving(false)
    }
  }

  const approving = choice === 'APPROVED'

  return (
    <GlassCard className="space-y-4 p-6">
      <div>
        <h2 className="text-lg font-semibold">Decision</h2>
        <p className="mt-1 text-sm text-muted">
          Approve the application, or reject it with a note explaining what the driver needs to fix. The driver is
          notified either way.
        </p>
      </div>

      <TextAreaField
        label="Note to the driver"
        value={note}
        maxLength={NOTE_MAX}
        onChange={(e) => {
          setNote(e.target.value)
          setNoteError('')
          setChoice(null)
        }}
        error={noteError}
        hint={`Required when rejecting, optional when approving. ${note.length} of ${NOTE_MAX} characters.`}
        disabled={saving}
      />

      {error && (
        <p role="alert" className="rounded-2xl bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
          {error}
        </p>
      )}

      {choice === null ? (
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => choose('APPROVED')}>
            <CircleCheck className="h-4 w-4" aria-hidden="true" />
            Approve
          </Button>
          <Button variant="secondary" onClick={() => choose('REJECTED')}>
            <CircleX className="h-4 w-4" aria-hidden="true" />
            Reject
          </Button>
        </div>
      ) : (
        <div role="group" aria-label="Confirm decision" className="rounded-2xl border border-line p-4">
          <p className="font-medium">
            {approving ? 'Approve this application?' : 'Reject this application?'}
          </p>
          <p className="mt-1 text-sm text-muted">
            {approving
              ? 'The driver will be notified that they are approved.'
              : 'The driver will be notified and shown your note, and can then update and resubmit.'}
          </p>
          <div className="mt-3 flex flex-wrap gap-3">
            <Button onClick={() => void confirm()} loading={saving} loadingLabel="Saving decision">
              {approving ? 'Yes, approve' : 'Yes, reject'}
            </Button>
            <Button variant="ghost" onClick={() => setChoice(null)} disabled={saving}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </GlassCard>
  )
}
