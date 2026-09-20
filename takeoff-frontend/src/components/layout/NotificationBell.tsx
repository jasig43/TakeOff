import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCheck, CircleCheck, CircleX, Info, type LucideIcon } from 'lucide-react'
import { notificationApi } from '../../api/applicationApi'
import type { NotificationInbox, NotificationItem } from '../../api/types'
import { useToast } from '../../hooks/useToast'
import { usePolledResource } from '../../hooks/usePolledResource'
import { errorMessage } from '../../utils/apiHelpers'
import { formatDateTime } from '../../utils/formatting'
import { BellPopover } from './BellPopover'

const PREVIEW_COUNT = 5

// Module-level so the polled loader keeps one identity between renders.
const loadInbox = () => notificationApi.inbox()

function iconFor(item: NotificationItem): { icon: LucideIcon; tone: string } {
  if (item.type === 'APPLICATION_APPROVED') return { icon: CircleCheck, tone: 'text-success' }
  if (item.type === 'APPLICATION_REJECTED') return { icon: CircleX, tone: 'text-danger' }
  return { icon: Info, tone: 'text-brand' }
}

/** A driver's bell: unread count on the badge and the latest notifications in the panel. */
export function NotificationBell() {
  const toast = useToast()
  const { data: inbox, replace } = usePolledResource<NotificationInbox>(true, loadInbox)
  const [busy, setBusy] = useState(false)

  const unread = inbox?.unreadCount ?? 0
  const items = inbox?.items.slice(0, PREVIEW_COUNT) ?? []

  const apply = async (action: () => Promise<NotificationInbox>) => {
    setBusy(true)
    try {
      replace(await action())
    } catch (failure) {
      toast.error(errorMessage(failure, 'We could not update your notifications. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <BellPopover
      buttonLabel={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
      panelLabel="Notifications"
      count={unread}
    >
      {(close) => (
        <div>
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold">Notifications</h2>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => void apply(notificationApi.markAllRead)}
                disabled={busy}
                className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-semibold text-brand transition hover:bg-brand-soft disabled:opacity-55"
              >
                <CheckCheck className="h-4 w-4" aria-hidden="true" />
                Mark all as read
              </button>
            )}
          </div>

          {items.length === 0 ? (
            <p className="mt-4 text-sm text-muted">You have no notifications yet. We will tell you here when your application changes.</p>
          ) : (
            <ul className="mt-3 divide-y divide-line">
              {items.map((item) => {
                const { icon: Icon, tone } = iconFor(item)
                return (
                  <li key={item.id} className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
                    <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${tone}`} aria-hidden="true" />
                    <div className="min-w-0 flex-1">
                      <p className={`text-sm ${item.read ? 'font-medium' : 'font-bold'}`}>
                        {!item.read && <span className="sr-only">Unread: </span>}
                        {item.title}
                      </p>
                      <p className="mt-0.5 line-clamp-2 text-sm text-muted">{item.message}</p>
                      <p className="mt-1 text-xs text-muted">{formatDateTime(item.createdAt)}</p>
                    </div>
                    {!item.read && (
                      <button
                        type="button"
                        onClick={() => void apply(() => notificationApi.markRead(item.id))}
                        disabled={busy}
                        aria-label={`Mark "${item.title}" as read`}
                        className="shrink-0 rounded-lg px-2 py-1 text-xs font-semibold text-brand transition hover:bg-brand-soft disabled:opacity-55"
                      >
                        Mark read
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          )}

          <Link
            to="/driver/notifications"
            onClick={close}
            className="mt-4 block rounded-xl border border-line px-4 py-2.5 text-center text-sm font-semibold text-brand transition hover:bg-brand-soft"
          >
            View all notifications
          </Link>
        </div>
      )}
    </BellPopover>
  )
}
