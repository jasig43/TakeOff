import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { clearSession, getAccessToken } from '../utils/tokenStorage'
import type { ApiErrorBody } from './types'

const DEFAULT_BASE_URL = 'http://localhost:8080/api/v1'

/** Fired when the server rejects a token we sent (expired / revoked). AuthContext listens and signs the user out. */
export const UNAUTHORIZED_EVENT = 'takeoff:unauthorized'

const NETWORK_MESSAGE = "We couldn't reach the TakeOFF server. Check your connection and try again."
const TIMEOUT_MESSAGE = 'The TakeOFF server is taking longer than usual to respond. It may be waking up, so please try again in a moment.'
const SERVER_MESSAGE = 'Something went wrong on our side. Please try again in a moment.'
const FALLBACK_MESSAGE = 'Something went wrong. Please try again.'

/** A normalized API failure with a user-safe message and per-field validation errors. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** Field name -> validation messages, when the server rejected specific inputs. */
  readonly fieldErrors: Record<string, string[]>
  readonly isNetworkError: boolean

  constructor(init: {
    message: string
    status?: number
    code?: string
    fieldErrors?: Record<string, string[]>
    isNetworkError?: boolean
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status ?? 0
    this.code = init.code ?? 'UNKNOWN'
    this.fieldErrors = init.fieldErrors ?? {}
    this.isNetworkError = init.isNetworkError ?? false
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/**
 * A free hosted API goes to sleep when idle and needs a minute or more to wake (Spring Boot start-up on a small
 * instance), during which the host holds the request rather than refusing it. The timeout has to outlast that, or the
 * first visitor of the day sees a failure. Genuine connection failures (offline, refused, blocked) still fail at once.
 */
export const REQUEST_TIMEOUT_MS = 90_000

export const apiClient: AxiosInstance = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL as string | undefined) || DEFAULT_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
})

// Centralized bearer-token attachment.
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiError(error)),
)

function toApiError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return new ApiError({ message: FALLBACK_MESSAGE })
  }
  const axiosError = error as AxiosError<Partial<ApiErrorBody>>

  if (!axiosError.response) {
    // Offline, DNS failure, CORS rejection, refused connection or timeout.
    if (import.meta.env.DEV) console.debug('[api] network error', axiosError.message)
    const timedOut = axiosError.code === 'ECONNABORTED' || axiosError.code === 'ETIMEDOUT'
    return new ApiError({ message: timedOut ? TIMEOUT_MESSAGE : NETWORK_MESSAGE, isNetworkError: true })
  }

  const { status, data } = axiosError.response
  if (import.meta.env.DEV) console.debug('[api] error response', status, data)

  const hadToken = Boolean(axiosError.config?.headers?.get?.('Authorization'))
  const isAuthEndpoint = (axiosError.config?.url ?? '').includes('/auth/')
  // An expired/invalid token on a protected call: drop it once and let the UI redirect.
  // Auth endpoints are excluded so a wrong password can't trigger a sign-out loop.
  if (status === 401 && hadToken && !isAuthEndpoint) {
    clearSession()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }

  const fieldErrors: Record<string, string[]> = {}
  for (const fe of data?.fieldErrors ?? []) {
    ;(fieldErrors[fe.field] ??= []).push(fe.message)
  }

  // Backend messages for 4xx are written to be user-safe; 5xx details stay in the server logs.
  const message = status >= 500 ? SERVER_MESSAGE : data?.message || FALLBACK_MESSAGE

  return new ApiError({ message, status, code: data?.code, fieldErrors })
}
