import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient, REQUEST_TIMEOUT_MS } from '../../api/client'
import { ServerWakeNotice } from './ServerWakeNotice'

const HOSTED = 'https://takeoff-api.example.com/api/v1'
const LOCAL = apiClient.defaults.baseURL

function answer(ok: boolean) {
  return Promise.resolve({ ok } as Response)
}

describe('server wake-up notice', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', fetchMock)
    apiClient.defaults.baseURL = HOSTED
  })
  afterEach(() => {
    apiClient.defaults.baseURL = LOCAL
    vi.unstubAllGlobals()
    vi.useRealTimers()
    fetchMock.mockReset()
  })

  it('waits longer than a free host needs to wake up before it gives up on a request', () => {
    expect(REQUEST_TIMEOUT_MS).toBeGreaterThanOrEqual(90_000)
    expect(apiClient.defaults.timeout).toBe(REQUEST_TIMEOUT_MS)
  })

  it('stays out of the way when the API answers straight away', async () => {
    fetchMock.mockReturnValue(answer(true))
    render(<ServerWakeNotice />)

    await act(() => vi.advanceTimersByTimeAsync(10_000))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe(`${HOSTED}/health`)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('tells the visitor the server is waking when it has not answered after a few seconds, then goes away', async () => {
    let wake: (response: Response) => void = () => undefined
    fetchMock.mockReturnValue(new Promise<Response>((resolve) => (wake = resolve)))
    render(<ServerWakeNotice />)

    await act(() => vi.advanceTimersByTimeAsync(2_000))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(2_000))
    expect(screen.getByRole('status')).toHaveTextContent(/waking up the takeoff server/i)

    await act(async () => wake({ ok: true } as Response))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('keeps asking while the host answers with an error or refuses, and only stops once it is up', async () => {
    fetchMock
      .mockReturnValueOnce(answer(false)) // the host's proxy answering "not ready" while the app boots
      .mockRejectedValueOnce(new TypeError('failed to fetch'))
      .mockReturnValue(answer(true))
    render(<ServerWakeNotice />)

    await act(() => vi.advanceTimersByTimeAsync(4_000))
    expect(screen.getByRole('status')).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(10_000))
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('does not ask a local API anything: there a slow answer means something else', async () => {
    apiClient.defaults.baseURL = 'http://localhost:8080/api/v1'
    fetchMock.mockReturnValue(new Promise<Response>(() => undefined))
    render(<ServerWakeNotice />)

    await act(() => vi.advanceTimersByTimeAsync(20_000))

    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('stops asking when the page is left', async () => {
    fetchMock.mockReturnValue(answer(false))
    const { unmount } = render(<ServerWakeNotice />)
    await act(() => vi.advanceTimersByTimeAsync(4_000))
    const before = fetchMock.mock.calls.length

    unmount()
    await act(() => vi.advanceTimersByTimeAsync(30_000))

    expect(fetchMock).toHaveBeenCalledTimes(before)
  })
})
