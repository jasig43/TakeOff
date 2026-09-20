import { useContext, useMemo } from 'react'
import { ToastContext } from '../context/toast'

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  const { notify } = ctx
  return useMemo(
    () => ({
      success: (message: string) => notify('success', message),
      error: (message: string) => notify('error', message),
      info: (message: string) => notify('info', message),
    }),
    [notify],
  )
}
