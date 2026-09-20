import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { notificationApi } from '../api/applicationApi'

/** Fired by pages after they change notifications, so the sidebar badge updates immediately. */
export const NOTIFICATIONS_CHANGED_EVENT = 'takeoff:notifications-changed'

const REFRESH_MS = 60_000

/**
 * Unread-notification count for the sidebar badge (drivers only; pass `enabled=false` for other roles).
 * It refreshes on navigation, on the change event, and once a minute. Failures are ignored: a missing badge is
 * better than an error in the navigation.
 */
export function useUnreadNotifications(enabled: boolean): number {
  const [count, setCount] = useState(0)
  const { pathname } = useLocation()
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!enabled) return
    const bump = () => setTick((n) => n + 1)
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, bump)
    const interval = setInterval(bump, REFRESH_MS)
    return () => {
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, bump)
      clearInterval(interval)
    }
  }, [enabled])

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    notificationApi.inbox().then(
      (inbox) => {
        if (!cancelled) setCount(inbox.unreadCount)
      },
      () => {
        /* ignore */
      },
    )
    return () => {
      cancelled = true
    }
  }, [enabled, pathname, tick])

  return enabled ? count : 0
}
