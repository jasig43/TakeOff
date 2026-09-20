import { useState } from 'react'
import { Bell, CheckCheck, CircleCheck, CircleX, Info, RefreshCw, type LucideIcon } from 'lucide-react'
import { notificationApi } from '../api/applicationApi'
import type { NotificationInbox, NotificationItem } from '../api/types'
import { AppShell } from '../components/layout/AppShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { useApiResource } from '../hooks/useApiResource'
import { useToast } from '../hooks/useToast'
import { NOTIFICATIONS_CHANGED_EVENT } from '../hooks/usePolledResource'
import { usePageTitle } from '../hooks/usePageTitle'
import { errorMessage } from '../utils/apiHelpers'
import { formatDateTime } from '../utils/formatting'

function iconFor(item: NotificationItem): { icon: LucideIcon; tone: string } {
  if (item.type === 'APPLICATION_APPROVED') return { icon: CircleCheck, tone: 'text-success' }
  if (item.type === 'APPLICATION_REJECTED') return { icon: CircleX, tone: 'text-danger' }
  return { icon: Info, tone: 'text-brand' }
}

export default function DriverNotificationsPage() {
  usePageTitle('Notifications')
  const toast = useToast()
  const { data, error, loading, reload } = useApiResource(notificationApi.inbox, 'We could not load your notifications.')
  // The inbox returned by mark-as-read calls replaces the loaded one.
  const [updated, setUpdated] = useState<NotificationInbox | null>(null)
  const [busy, setBusy] = useState(false)

  const inbox = updated ?? data

  const apply = async (action: () => Promise<NotificationInbox>) => {
    setBusy(true)
    try {
      setUpdated(await action())
      window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT))
    } catch (failure) {
      toast.error(errorMessage(failure, 'We could not update your notifications. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl space-y-6">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="font-display text-3xl font-bold">Notifications</h1>
            <p className="mt-2 text-muted">Updates about your application.</p>
          </div>
          {inbox && inbox.unreadCount > 0 && (
            <Button variant="secondary" onClick={() => void apply(notificationApi.markAllRead)} disabled={busy}>
              <CheckCheck className="h-4 w-4" aria-hidden="true" />
              Mark all as read
            </Button>
          )}
        </header>

        {loading && !inbox && (
          <GlassCard className="space-y-4 p-6" aria-busy="true" aria-label="Loading notifications">
            <Skeleton className="h-6 w-1/2" />
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-6 w-1/3" />
            <Skeleton className="h-5 w-full" />
          </GlassCard>
        )}

        {!loading && error && !inbox && (
          <GlassCard className="space-y-4 p-6" role="alert">
            <p className="font-medium text-danger">{error}</p>
            <Button variant="secondary" onClick={reload}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {inbox && inbox.items.length === 0 && (
          <GlassCard className="flex flex-col items-center gap-3 p-10 text-center">
            <Bell className="h-8 w-8 text-muted" aria-hidden="true" />
            <p className="font-medium">No notifications yet</p>
            <p className="text-sm text-muted">We will let you know here when there is news about your application.</p>
          </GlassCard>
        )}

        {inbox && inbox.items.length > 0 && (
          <ul className="space-y-3" aria-label="Notifications">
            {inbox.items.map((item) => {
              const { icon: Icon, tone } = iconFor(item)
              return (
                <li key={item.id}>
                  <GlassCard className={`p-5 ${item.read ? '' : 'border-brand'}`}>
                    <div className="flex items-start gap-4">
                      <Icon className={`mt-0.5 h-6 w-6 shrink-0 ${tone}`} aria-hidden="true" />
                      <div className="min-w-0 flex-1">
                        <h2 className={`text-base ${item.read ? 'font-medium' : 'font-bold'}`}>
                          {!item.read && <span className="sr-only">Unread: </span>}
                          {item.title}
                        </h2>
                        <p className="mt-1 whitespace-pre-line text-sm text-fg">{item.message}</p>
                        <p className="mt-2 text-xs text-muted">{formatDateTime(item.createdAt)}</p>
                      </div>
                      {!item.read && (
                        <Button
                          variant="ghost"
                          onClick={() => void apply(() => notificationApi.markRead(item.id))}
                          disabled={busy}
                          aria-label={`Mark "${item.title}" as read`}
                        >
                          Mark as read
                        </Button>
                      )}
                    </div>
                  </GlassCard>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </AppShell>
  )
}
