import { useRef, useState, type KeyboardEvent } from 'react'
import { KeyRound, Monitor, UserRound } from 'lucide-react'
import { ChangePasswordForm } from '../components/account/ChangePasswordForm'
import { UsersAndRoles } from '../components/account/UsersAndRoles'
import { AppShell } from '../components/layout/AppShell'
import { GlassCard } from '../components/ui/GlassCard'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { useToast } from '../hooks/useToast'
import { ROLE_LABEL } from '../utils/formatting'

type TabId = 'account' | 'users'

const TABS: { id: TabId; label: string }[] = [
  { id: 'account', label: 'Account and security' },
  { id: 'users', label: 'Users and roles' },
]

function Row({ label, children }: { label: string; children: string }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="break-all text-sm font-medium text-fg">{children}</dd>
    </div>
  )
}

export default function AdminSettingsPage() {
  usePageTitle('Settings')
  const { user, updateUser } = useAuth()
  const toast = useToast()
  const [tab, setTab] = useState<TabId>('account')
  const tabRefs = useRef<Record<TabId, HTMLButtonElement | null>>({ account: null, users: null })

  // Arrow keys move between tabs, as people expect from a tab list.
  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'Home' && event.key !== 'End') return
    event.preventDefault()
    const index = TABS.findIndex((t) => t.id === tab)
    const next =
      event.key === 'Home' ? 0 : event.key === 'End' ? TABS.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + TABS.length) % TABS.length
    setTab(TABS[next].id)
    tabRefs.current[TABS[next].id]?.focus()
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">Settings</h1>
          <p className="mt-2 text-muted">Your account, and the people who can sign in to TakeOFF.</p>
        </header>

        <div role="tablist" aria-label="Settings sections" className="flex gap-2 border-b border-line">
          {TABS.map(({ id, label }) => {
            const selected = tab === id
            return (
              <button
                key={id}
                ref={(node) => {
                  tabRefs.current[id] = node
                }}
                type="button"
                role="tab"
                id={`tab-${id}`}
                aria-selected={selected}
                aria-controls={`panel-${id}`}
                tabIndex={selected ? 0 : -1}
                onClick={() => setTab(id)}
                onKeyDown={onKeyDown}
                className={`-mb-px rounded-t-xl border-b-2 px-4 py-3 text-sm font-semibold transition ${
                  selected ? 'border-brand text-brand' : 'border-transparent text-muted hover:text-fg'
                }`}
              >
                {label}
              </button>
            )
          })}
        </div>

        {tab === 'account' && (
          <div role="tabpanel" id="panel-account" aria-labelledby="tab-account" className="max-w-3xl space-y-6">
            <GlassCard className="p-6">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <UserRound className="h-5 w-5 text-brand" aria-hidden="true" />
                Account
              </h2>
              {user && (
                <dl className="mt-4 space-y-3">
                  <Row label="Name">{user.fullName}</Row>
                  <Row label="Email">{user.email}</Row>
                  <Row label="Phone">{user.phoneNumber}</Row>
                  <Row label="Role">{ROLE_LABEL[user.role]}</Row>
                </dl>
              )}
            </GlassCard>

            <GlassCard className="p-6">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <KeyRound className="h-5 w-5 text-brand" aria-hidden="true" />
                Change password
              </h2>
              <p className="mt-1 text-sm text-muted">
                Use the same rules as sign-up. You stay signed in here; other devices that are already signed in stay signed
                in until their session expires.
              </p>
              <div className="mt-6">
                <ChangePasswordForm
                  onSuccess={(next) => {
                    updateUser(next)
                    toast.success('Your password has been changed.')
                  }}
                />
              </div>
            </GlassCard>

            <GlassCard className="p-6">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <Monitor className="h-5 w-5 text-brand" aria-hidden="true" />
                Appearance
              </h2>
              <p className="mt-2 text-sm text-muted">
                TakeOFF follows your device&apos;s light or dark setting automatically, so there is nothing to switch here.
              </p>
            </GlassCard>
          </div>
        )}

        {tab === 'users' && (
          <div role="tabpanel" id="panel-users" aria-labelledby="tab-users">
            <UsersAndRoles />
          </div>
        )}
      </div>
    </AppShell>
  )
}
