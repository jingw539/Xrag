import { describe, it, expect, vi } from 'vitest'
import { importCases } from './case.js'
import request from '@/utils/request'

// Mock the request utility
vi.mock('@/utils/request', () => {
  return {
    default: vi.fn()
  }
})

describe('api/case', () => {
  describe('importCases', () => {
    it('should append file to FormData and make correct request', () => {
      // Create a mock file
      const mockFile = new File(['content'], 'test.csv', { type: 'text/csv' })

      // Call the function
      importCases(mockFile)

      // Verify request was called
      expect(request).toHaveBeenCalled()

      // Get the argument passed to request
      const requestArg = request.mock.calls[0][0]

      // Verify url, method, and headers
      expect(requestArg.url).toBe('/cases/import')
      expect(requestArg.method).toBe('post')
      expect(requestArg.headers).toEqual({
        'Content-Type': 'multipart/form-data'
      })

      // Verify FormData contents
      const formData = requestArg.data
      expect(formData).toBeInstanceOf(FormData)
      expect(formData.get('file')).toBe(mockFile)
    })
  })
})
