import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { notificationApi, reviewApi } from '../../api/applicationApi'
import type { NotificationInbox, NotificationItem } from '../../api/types'
import { renderPage } from '../../test/renderPage'
import { TopBar } from './TopBar'

vi.mock('../../api/applicationApi', () => ({
  notificationApi: { inbox: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
  reviewApi: { summary: vi.fn() },
}))

function item(id: number, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    type: 'APPLICATION_SUBMITTED',
    title: `Title ${id}`,
    message: `Message ${id}`,
    read: false,
    createdAt: '2026-09-20T10:00:00Z',
    ...overrides,
  }
}

function inbox(items: NotificationItem[]): NotificationInbox {
  return { items, unreadCount: items.filter((i) => !i.read).length }
}

function renderBar(role: 'APPLICANT_DRIVER' | 'LOGISTICS_ADMIN') {
  return renderPage(<TopBar />, { role, route: '/' })
}

describe('top bar', () => {
  beforeEach(() => {
    vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([]))
    vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 0, approved: 0, rejected: 0, totalSubmitted: 0 })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('is a frosted-glass header fixed to the top of the screen so it stays put while the page scrolls', () => {
    renderBar('APPLICANT_DRIVER')

    const header = screen.getByRole('banner')
    expect(header).toHaveClass('fixed', 'top-0', 'glass-bar')
    expect(header).not.toHaveClass('bg-white', 'bg-surface-strong') // blends with the dashboard rather than a solid white bar
    // from the lg breakpoint it sits beside the sidebar, not under it
    expect(header).toHaveClass('lg:left-64')
  })

  describe('driver bell', () => {
    it('shows the unread count on the bell, and says it in words for screen readers', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([item(1), item(2), item(3, { read: true })]))
      renderBar('APPLICANT_DRIVER')

      const bell = await screen.findByRole('button', { name: 'Notifications, 2 unread' })
      expect(bell).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(notificationApi.inbox).toHaveBeenCalled()
      expect(reviewApi.summary).not.toHaveBeenCalled() // an admin-only call
    })

    it('opens a panel with the latest notifications and a link to all of them', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([item(1), item(2, { read: true })]))
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))

      const panel = screen.getByRole('dialog', { name: 'Notifications' })
      expect(within(panel).getByText('Message 1')).toBeInTheDocument()
      expect(within(panel).getByText('Message 2')).toBeInTheDocument()
      expect(within(panel).getByText('Unread:', { exact: false })).toBeInTheDocument()
      expect(within(panel).getByRole('link', { name: 'View all notifications' })).toHaveAttribute(
        'href',
        '/driver/notifications',
      )
    })

    it('shows only the five most recent ones in the panel', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([1, 2, 3, 4, 5, 6, 7].map((id) => item(id, { read: true }))))
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications' }))

      expect(within(screen.getByRole('dialog')).getAllByRole('listitem')).toHaveLength(5)
    })

    it('says so when there is nothing yet', async () => {
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications' }))

      expect(screen.getByText(/you have no notifications yet/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /mark all as read/i })).not.toBeInTheDocument()
    })

    it('marks everything read and clears the badge', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([item(1), item(2)]))
      vi.mocked(notificationApi.markAllRead).mockResolvedValue(inbox([item(1, { read: true }), item(2, { read: true })]))
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
      await user.click(screen.getByRole('button', { name: /mark all as read/i }))

      expect(notificationApi.markAllRead).toHaveBeenCalled()
      expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /mark all as read/i })).not.toBeInTheDocument()
    })

    it('marks a single notification read', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([item(1), item(2)]))
      vi.mocked(notificationApi.markRead).mockResolvedValue(inbox([item(1, { read: true }), item(2)]))
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
      await user.click(screen.getByRole('button', { name: 'Mark "Title 1" as read' }))

      expect(notificationApi.markRead).toHaveBeenCalledWith(1)
      expect(await screen.findByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
    })

    it('tells the driver when marking as read fails, and leaves the badge alone', async () => {
      vi.mocked(notificationApi.inbox).mockResolvedValue(inbox([item(1)]))
      vi.mocked(notificationApi.markAllRead).mockRejectedValue(new Error('offline'))
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
      await user.click(screen.getByRole('button', { name: /mark all as read/i }))

      expect(await screen.findByText(/could not update your notifications/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
    })

    it('closes with Escape and returns focus to the bell', async () => {
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      const bell = await screen.findByRole('button', { name: 'Notifications' })
      await user.click(bell)
      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(bell).toHaveAttribute('aria-expanded', 'true')

      await user.keyboard('{Escape}')

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(bell).toHaveFocus()
    })

    it('closes when the user clicks elsewhere, and toggles with the bell itself', async () => {
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      const bell = await screen.findByRole('button', { name: 'Notifications' })
      await user.click(bell)
      await user.click(bell)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      await user.click(bell)
      expect(screen.getByRole('dialog')).toBeInTheDocument()
      await user.click(document.body)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('closes the panel when following the link to all notifications', async () => {
      const user = userEvent.setup()
      renderBar('APPLICANT_DRIVER')

      await user.click(await screen.findByRole('button', { name: 'Notifications' }))
      await user.click(screen.getByRole('link', { name: 'View all notifications' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByTestId('location')).toHaveTextContent('/driver/notifications')
    })
  })

  describe('administrator bell', () => {
    it('shows how many applications are waiting for review and links to that queue', async () => {
      vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 4, approved: 1, rejected: 0, totalSubmitted: 5 })
      const user = userEvent.setup()
      renderBar('LOGISTICS_ADMIN')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 4 waiting for review' }))

      const panel = screen.getByRole('dialog', { name: 'Notifications' })
      expect(within(panel).getByText(/applications are/)).toBeInTheDocument()
      expect(within(panel).getByRole('link', { name: 'Open the review queue' })).toHaveAttribute(
        'href',
        '/admin/applications?status=PENDING_REVIEW',
      )
      expect(notificationApi.inbox).not.toHaveBeenCalled() // drivers' notifications are never fetched for an admin
    })

    it('uses the singular for one application', async () => {
      vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 1, approved: 0, rejected: 0, totalSubmitted: 1 })
      const user = userEvent.setup()
      renderBar('LOGISTICS_ADMIN')

      await user.click(await screen.findByRole('button', { name: 'Notifications, 1 waiting for review' }))

      expect(screen.getByText(/application is/)).toBeInTheDocument()
    })

    it('says the queue is empty when nothing is waiting', async () => {
      const user = userEvent.setup()
      renderBar('LOGISTICS_ADMIN')

      await user.click(await screen.findByRole('button', { name: 'Notifications' }))

      expect(screen.getByText(/you are all caught up/i)).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Open the review queue' })).not.toBeInTheDocument()
    })

    it('still renders the bell, without a number, when the counts cannot be loaded', async () => {
      vi.mocked(reviewApi.summary).mockRejectedValue(new Error('down'))
      renderBar('LOGISTICS_ADMIN')

      expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument()
    })
  })
})
