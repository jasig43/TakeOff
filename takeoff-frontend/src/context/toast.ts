import { createContext } from 'react'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

export interface ToastContextValue {
  toasts: ToastItem[]
  notify: (kind: ToastKind, message: string) => void
  dismiss: (id: number) => void
}

export const ToastContext = createContext<ToastContextValue | undefined>(undefined)
