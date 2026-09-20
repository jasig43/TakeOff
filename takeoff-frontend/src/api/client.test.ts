import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { apiClient, isApiError } from './client'

const original = apiClient.defaults.adapter

function failWith(code: string, message: string) {
  apiClient.defaults.adapter = (config) => Promise.reject(new AxiosError(message, code, config as InternalAxiosRequestConfig))
}

async function messageOfFailedCall(): Promise<string> {
  try {
    await apiClient.get('/anything')
  } catch (error) {
    if (isApiError(error)) return error.message
  }
  throw new Error('the call should have failed with an ApiError')
}

describe('api client failures without an answer from the server', () => {
  afterEach(() => {
    apiClient.defaults.adapter = original
  })

  it('says a timeout may just be the server waking up, so the person tries again rather than giving up', async () => {
    failWith(AxiosError.ECONNABORTED, 'timeout of 90000ms exceeded')

    expect(await messageOfFailedCall()).toMatch(/taking longer than usual.*waking up/i)
  })

  it('says the server could not be reached for a refused connection or being offline', async () => {
    failWith(AxiosError.ERR_NETWORK, 'Network Error')

    expect(await messageOfFailedCall()).toMatch(/couldn't reach the takeoff server/i)
  })
})
