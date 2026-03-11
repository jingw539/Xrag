import { describe, it, expect, vi, beforeEach } from 'vitest'
import request from './request'
import { ElMessage } from 'element-plus'

// Mock dependencies
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn()
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    token: 'fake-token',
    refreshToken: 'fake-refresh',
    logout: vi.fn(),
    setToken: vi.fn()
  }))
}))

vi.mock('@/router', () => ({
  default: {
    push: vi.fn()
  }
}))

describe('request interceptors', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should handle ECONNABORTED timeout errors correctly', async () => {
    const timeoutError = new Error('timeout of 150000ms exceeded');
    timeoutError.code = 'ECONNABORTED';
    timeoutError.config = {};

    try {
      await request.get('/test-timeout', {
        adapter: async () => {
          return Promise.reject(timeoutError)
        }
      })
      // Should not reach here
      expect(true).toBe(false)
    } catch (e) {
      expect(e).toBe(timeoutError)
    }

    expect(ElMessage.error).toHaveBeenCalledWith('请求超时，AI服务处理时间较长，请稍后重试')
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
  })

  it('should not handle ECONNABORTED errors without timeout in message as timeout errors', async () => {
    const abortError = new Error('request aborted');
    abortError.code = 'ECONNABORTED';
    abortError.config = {};

    try {
      await request.get('/test-abort', {
        adapter: async () => {
          return Promise.reject(abortError)
        }
      })
      expect(true).toBe(false)
    } catch (e) {
      expect(e).toBe(abortError)
    }

    // Should fall through to the network error handling if no response,
    // which gives '网络错误，请检查网络连接'
    expect(ElMessage.error).toHaveBeenCalledWith('网络错误，请检查网络连接')
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
  })
})
