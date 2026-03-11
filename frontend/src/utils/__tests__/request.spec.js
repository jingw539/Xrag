import { describe, it, expect, vi, beforeEach } from 'vitest'

// We will export the interceptor from our mock
vi.mock('axios', () => {
  let errorInterceptorCallback = null

  const mockInstance = vi.fn(async (config) => {
    return { data: config.url + '-success' }
  })

  mockInstance.interceptors = {
    request: { use: vi.fn() },
    response: {
      use: vi.fn((successCb, errorCb) => {
        errorInterceptorCallback = errorCb
      })
    }
  }

  return {
    default: {
      create: vi.fn(() => mockInstance),
      post: vi.fn(),
      getInterceptor: () => errorInterceptorCallback
    }
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

vi.mock('@/router', () => ({
  default: { push: vi.fn() }
}))

import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { createPinia, setActivePinia } from 'pinia'
import request from '../request'

describe('request util - concurrency lock logic', () => {
  let userStore
  let errorInterceptorCallback

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
    vi.clearAllMocks()

    // Setup initial store values
    userStore.token = 'old-token'
    userStore.refreshToken = 'valid-refresh-token'
    userStore.setToken = vi.fn(function(token) { this.token = token })
    userStore.logout = vi.fn()

    errorInterceptorCallback = axios.getInterceptor()
  })

  it('should wait for refresh token if already refreshing', async () => {
    const config1 = { url: '/api/data1', headers: {} }
    const error1 = { response: { status: 401 }, config: config1 }

    const config2 = { url: '/api/data2', headers: {} }
    const error2 = { response: { status: 401 }, config: config2 }

    const config3 = { url: '/api/data3', headers: {} }
    const error3 = { response: { status: 401 }, config: config3 }

    let resolveRefresh
    axios.post.mockImplementation(() => new Promise((resolve) => {
      resolveRefresh = resolve
    }))

    const promise1 = errorInterceptorCallback(error1)

    await new Promise(r => setTimeout(r, 0))

    const promise2 = errorInterceptorCallback(error2)
    const promise3 = errorInterceptorCallback(error3)

    expect(axios.post).toHaveBeenCalledTimes(1)
    expect(axios.post).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'valid-refresh-token' })

    const newToken = 'new-access-token'
    resolveRefresh({ data: { data: { accessToken: newToken } } })

    const [res1, res2, res3] = await Promise.all([promise1, promise2, promise3])

    expect(userStore.setToken).toHaveBeenCalledWith(newToken)
    expect(request).toHaveBeenCalledTimes(3)
    expect(config1.headers.Authorization).toBe(`Bearer ${newToken}`)
    expect(config2.headers.Authorization).toBe(`Bearer ${newToken}`)
    expect(config3.headers.Authorization).toBe(`Bearer ${newToken}`)

    expect(res1).toEqual({ data: '/api/data1-success' })
    expect(res2).toEqual({ data: '/api/data2-success' })
    expect(res3).toEqual({ data: '/api/data3-success' })
  })

  it('should reject queued requests if refresh token fails', async () => {
    const router = (await import('@/router')).default

    const config1 = { url: '/api/data1', headers: {} }
    const error1 = { response: { status: 401 }, config: config1 }

    const config2 = { url: '/api/data2', headers: {} }
    const error2 = { response: { status: 401 }, config: config2 }

    let rejectRefresh
    axios.post.mockImplementation(() => new Promise((resolve, reject) => {
      rejectRefresh = reject
    }))

    const promise1 = errorInterceptorCallback(error1)
    await new Promise(r => setTimeout(r, 0))
    const promise2 = errorInterceptorCallback(error2)

    const refreshError = new Error('Refresh failed')
    rejectRefresh(refreshError)

    await expect(promise1).rejects.toEqual(error1)
    await expect(promise2).rejects.toEqual(refreshError)

    expect(userStore.logout).toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith('/login')
  })
})
