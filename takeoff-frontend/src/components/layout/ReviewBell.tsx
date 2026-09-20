import { Link } from 'react-router-dom'
import { reviewApi } from '../../api/applicationApi'
import type { AdminSummary } from '../../api/types'
import { usePolledResource } from '../../hooks/usePolledResource'
import { BellPopover } from './BellPopover'

// Module-level so the polled loader keeps one identity between renders.
const loadSummary = () => reviewApi.summary()

/** An administrator's bell: how many applications are waiting for review, with a shortcut to them. */
export function ReviewBell() {
  const { data: summary } = usePolledResource<AdminSummary>(true, loadSummary)
  const waiting = summary?.pendingReview ?? 0

  return (
    <BellPopover
      buttonLabel={waiting > 0 ? `Notifications, ${waiting} waiting for review` : 'Notifications'}
      panelLabel="Notifications"
      count={waiting}
    >
      {(close) => (
        <div>
          <h2 className="text-base font-semibold">Notifications</h2>
          {waiting > 0 ? (
            <>
              <p className="mt-3 text-sm">
                <span className="font-bold">{waiting}</span> {waiting === 1 ? 'application is' : 'applications are'} waiting
                for your review.
              </p>
              <Link
                to="/admin/applications?status=PENDING_REVIEW"
                onClick={close}
                className="mt-4 block rounded-xl border border-line px-4 py-2.5 text-center text-sm font-semibold text-brand transition hover:bg-brand-soft"
              >
                Open the review queue
              </Link>
            </>
          ) : (
            <p className="mt-3 text-sm text-muted">You are all caught up. New submissions will appear here.</p>
          )}
        </div>
      )}
    </BellPopover>
  )
}
