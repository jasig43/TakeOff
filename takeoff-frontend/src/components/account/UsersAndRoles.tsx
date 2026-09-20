import { useCallback, useState } from 'react'
import { ChevronLeft, ChevronRight, KeyRound, RefreshCw, SearchX, UserPlus } from 'lucide-react'
import { adminApi } from '../../api/authApi'
import type { AdminUser, IssuedCredential, Role } from '../../api/types'
import { useApiResource } from '../../hooks/useApiResource'
import { useAuth } from '../../hooks/useAuth'
import { useDebounce } from '../../hooks/useDebounce'
import { useToast } from '../../hooks/useToast'
import { errorMessage } from '../../utils/apiHelpers'
import { ROLES, ROLE_LABEL, formatDateTime } from '../../utils/formatting'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { Skeleton } from '../ui/Skeleton'
import { TextField } from '../ui/TextField'
import { CreateAccountForm } from './CreateAccountForm'
import { TemporaryPasswordNotice } from './TemporaryPasswordNotice'

const PAGE_SIZE = 10

type Confirmation = { kind: 'role' | 'reset'; id: number } | null
type Issued = { credential: IssuedCredential; heading: string } | null

function StatusCell({ user, now }: { user: AdminUser; now: number }) {
  if (!user.mustChangePassword) {
    return <span className="text-sm text-muted">Active</span>
  }
  const expired = user.temporaryPasswordExpiresAt !== null && new Date(user.temporaryPasswordExpiresAt).getTime() <= now
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${expired ? 'bg-danger-soft text-danger' : 'bg-warning-soft text-warning'}`}>
      {expired ? 'Temporary password expired' : `Temporary password until ${formatDateTime(user.temporaryPasswordExpiresAt)}`}
    </span>
  )
}

/**
 * The administrator's account management: see everyone, create an account with a temporary password, assign a role, and
 * issue a new temporary password. Role changes and password issues ask for confirmation first. An administrator cannot
 * change their own role or reset their own password here (the server refuses too), so the last one can't be removed.
 */
export function UsersAndRoles() {
  const { user: me } = useAuth()
  const toast = useToast()

  const [text, setText] = useState('')
  const query = useDebounce(text.trim())
  const [page, setPage] = useState(0)

  const [creating, setCreating] = useState(false)
  const [issued, setIssued] = useState<Issued>(null)
  const [pendingRole, setPendingRole] = useState<Record<number, Role>>({})
  const [confirming, setConfirming] = useState<Confirmation>(null)
  const [busy, setBusy] = useState(false)
  // When the page was opened; used only to mark temporary passwords that have already run out.
  const [now] = useState(() => Date.now())

  const fetchPage = useCallback(() => adminApi.listUsers({ q: query, page, size: PAGE_SIZE }), [query, page])
  const { data, error, loading, reload } = useApiResource(fetchPage, 'We could not load the accounts.')
  const rows = data?.items ?? []

  const run = async (action: () => Promise<void>, failure: string) => {
    setBusy(true)
    try {
      await action()
    } catch (problem) {
      toast.error(errorMessage(problem, failure))
    } finally {
      setBusy(false)
    }
  }

  const saveRole = (account: AdminUser) =>
    run(async () => {
      const role = pendingRole[account.id]
      await adminApi.setUserRole(account.id, role)
      setPendingRole(({ [account.id]: _done, ...rest }) => rest)
      setConfirming(null)
      toast.success(`${account.fullName} is now ${ROLE_LABEL[role].toLowerCase() === 'administrator' ? 'an administrator' : 'a driver'}.`)
      reload()
    }, 'We could not change the role. Please try again.')

  const issuePassword = (account: AdminUser) =>
    run(async () => {
      const credential = await adminApi.issueTemporaryPassword(account.id)
      setConfirming(null)
      setCreating(false)
      setIssued({ credential, heading: 'New temporary password issued' })
      reload()
    }, 'We could not issue a new temporary password. Please try again.')

  return (
    <div className="space-y-6">
      <GlassCard className="p-5">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-[14rem] flex-1">
            <TextField
              label="Search accounts"
              type="search"
              placeholder="Name, email or phone"
              autoComplete="off"
              value={text}
              onChange={(e) => {
                setText(e.target.value)
                setPage(0)
              }}
            />
          </div>
          <Button
            onClick={() => {
              setCreating(true)
              setIssued(null)
            }}
            disabled={creating}
          >
            <UserPlus className="h-4 w-4" aria-hidden="true" />
            Create account
          </Button>
        </div>
      </GlassCard>

      {creating && (
        <CreateAccountForm
          onCancel={() => setCreating(false)}
          onCreated={(credential) => {
            setCreating(false)
            setIssued({ credential, heading: 'Account created' })
            setPage(0)
            reload()
          }}
        />
      )}

      {issued && <TemporaryPasswordNotice credential={issued.credential} heading={issued.heading} onDone={() => setIssued(null)} />}

      {error && (
        <GlassCard className="space-y-4 p-6" role="alert">
          <p className="font-medium text-danger">{error}</p>
          <Button variant="secondary" onClick={reload}>
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Try again
          </Button>
        </GlassCard>
      )}

      {!error && loading && !data && (
        <GlassCard className="space-y-4 p-6" aria-busy="true" aria-label="Loading accounts">
          {[0, 1, 2].map((n) => (
            <Skeleton key={n} className="h-10 w-full" />
          ))}
        </GlassCard>
      )}

      {!error && data && rows.length === 0 && (
        <GlassCard className="flex flex-col items-center gap-3 p-10 text-center">
          <SearchX className="h-8 w-8 text-muted" aria-hidden="true" />
          <p className="font-medium">No accounts found</p>
          <p className="text-sm text-muted">Try a different search.</p>
        </GlassCard>
      )}

      {!error && data && rows.length > 0 && (
        <GlassCard className="overflow-hidden" aria-busy={loading}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[46rem] text-left text-sm">
              <caption className="sr-only">Accounts</caption>
              <thead className="border-b border-line text-muted">
                <tr>
                  <th scope="col" className="px-5 py-3 font-medium">
                    Account
                  </th>
                  <th scope="col" className="px-5 py-3 font-medium">
                    Role
                  </th>
                  <th scope="col" className="px-5 py-3 font-medium">
                    Status
                  </th>
                  <th scope="col" className="px-5 py-3 font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((account) => {
                  const isMe = account.id === me?.id
                  const chosen = pendingRole[account.id] ?? account.role
                  const changed = chosen !== account.role
                  const confirmingThis = confirming?.id === account.id ? confirming.kind : null
                  return (
                    <RowGroup key={account.id}>
                      <tr className="align-middle">
                        <td className="px-5 py-3">
                          <p className="font-medium">
                            {account.fullName}
                            {isMe && <span className="ml-2 rounded-full bg-brand-soft px-2 py-0.5 text-xs font-semibold text-brand">You</span>}
                          </p>
                          <p className="break-all text-muted">{account.email}</p>
                          <p className="text-muted">{account.phoneNumber}</p>
                        </td>
                        <td className="px-5 py-3">
                          <select
                            aria-label={`Role for ${account.fullName}`}
                            className="field !w-auto min-w-[9.5rem] !py-2 text-sm"
                            value={chosen}
                            disabled={isMe || busy}
                            title={isMe ? 'You cannot change your own role' : undefined}
                            onChange={(e) => {
                              const next = e.target.value as Role
                              setConfirming(null)
                              setPendingRole((current) => {
                                if (next === account.role) {
                                  const { [account.id]: _dropped, ...rest } = current
                                  return rest
                                }
                                return { ...current, [account.id]: next }
                              })
                            }}
                          >
                            {ROLES.map((value) => (
                              <option key={value} value={value}>
                                {ROLE_LABEL[value]}
                              </option>
                            ))}
                          </select>
                          {changed && !confirmingThis && (
                            <span className="ml-2 inline-flex gap-1">
                              <Button variant="secondary" onClick={() => setConfirming({ kind: 'role', id: account.id })} aria-label={`Save role for ${account.fullName}`}>
                                Save
                              </Button>
                              <Button
                                variant="ghost"
                                onClick={() => setPendingRole(({ [account.id]: _dropped, ...rest }) => rest)}
                                aria-label={`Cancel role change for ${account.fullName}`}
                              >
                                Cancel
                              </Button>
                            </span>
                          )}
                        </td>
                        <td className="px-5 py-3">
                          <StatusCell user={account} now={now} />
                        </td>
                        <td className="px-5 py-3 text-right">
                          {!isMe && (
                            <Button
                              variant="ghost"
                              onClick={() => setConfirming({ kind: 'reset', id: account.id })}
                              disabled={busy}
                              aria-label={`Issue a new temporary password for ${account.fullName}`}
                            >
                              <KeyRound className="h-4 w-4" aria-hidden="true" />
                              New temporary password
                            </Button>
                          )}
                        </td>
                      </tr>
                      {confirmingThis && (
                        <tr>
                          <td colSpan={4} className="bg-brand-soft px-5 py-4">
                            <div role="group" aria-label={`Confirm for ${account.fullName}`} className="flex flex-wrap items-center gap-3">
                              <p className="min-w-[16rem] flex-1 text-sm">
                                {confirmingThis === 'role'
                                  ? `Make ${account.fullName} ${chosen === 'LOGISTICS_ADMIN' ? 'an administrator' : 'a driver'}? What they can see and do changes straight away, even if they are signed in now.`
                                  : `Issue ${account.fullName} a new temporary password? Their current password stops working immediately and they must choose a new one when they next sign in.`}
                              </p>
                              <Button
                                onClick={() => void (confirmingThis === 'role' ? saveRole(account) : issuePassword(account))}
                                loading={busy}
                                loadingLabel="Saving"
                              >
                                {confirmingThis === 'role' ? 'Yes, change role' : 'Yes, issue it'}
                              </Button>
                              <Button variant="ghost" onClick={() => setConfirming(null)} disabled={busy}>
                                Cancel
                              </Button>
                            </div>
                          </td>
                        </tr>
                      )}
                    </RowGroup>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3">
            <p className="text-sm text-muted" role="status">
              {data.totalItems} {data.totalItems === 1 ? 'account' : 'accounts'}, page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setPage(page - 1)} disabled={page <= 0}>
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                Previous
              </Button>
              <Button variant="secondary" onClick={() => setPage(page + 1)} disabled={page + 1 >= data.totalPages}>
                Next
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        </GlassCard>
      )}
    </div>
  )
}

/** A fragment that groups a data row with its optional confirmation row inside a table body. */
function RowGroup({ children }: { children: React.ReactNode }) {
  return <>{children}</>
}
