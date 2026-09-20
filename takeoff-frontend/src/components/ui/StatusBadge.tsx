import { CircleCheck, CircleX, Clock, FilePen, type LucideIcon } from 'lucide-react'
import type { ApplicationStatus } from '../../api/types'
import { STATUS_LABEL } from '../../utils/formatting'

const STYLE: Record<ApplicationStatus, { icon: LucideIcon; classes: string }> = {
  DRAFT: { icon: FilePen, classes: 'bg-brand-soft text-brand' },
  PENDING_REVIEW: { icon: Clock, classes: 'bg-accent-soft text-accent' },
  APPROVED: { icon: CircleCheck, classes: 'bg-success-soft text-success' },
  REJECTED: { icon: CircleX, classes: 'bg-danger-soft text-danger' },
}

/** Status pill. The meaning is carried by the icon and the words, never by colour alone. */
export function StatusBadge({ status }: { status: ApplicationStatus }) {
  const { icon: Icon, classes } = STYLE[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold ${classes}`}>
      <Icon className="h-4 w-4" aria-hidden="true" />
      {STATUS_LABEL[status]}
    </span>
  )
}
