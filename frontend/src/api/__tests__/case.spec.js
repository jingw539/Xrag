import { describe, it, expect, vi, beforeEach } from 'vitest'
import request from '@/utils/request'
import {
  listCases,
  getCaseById,
  claimCase,
  createCase,
  updateCase,
  deleteCase,
  markTypical,
  importCases
} from '../case'

// Mock the request utility
vi.mock('@/utils/request', () => {
  return {
    default: vi.fn()
  }
})

describe('case api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listCases calls request with correct parameters', async () => {
    const params = { page: 1, limit: 10 }
    await listCases(params)
    expect(request).toHaveBeenCalledWith({
      url: '/cases',
      method: 'get',
      params
    })
  })

  it('getCaseById calls request with correct url and method', async () => {
    const caseId = 123
    await getCaseById(caseId)
    expect(request).toHaveBeenCalledWith({
      url: `/cases/${caseId}`,
      method: 'get'
    })
  })

  it('claimCase calls request with correct url and method', async () => {
    const caseId = 123
    await claimCase(caseId)
    expect(request).toHaveBeenCalledWith({
      url: `/cases/${caseId}/claim`,
      method: 'post'
    })
  })

  it('createCase calls request with correct url, method and data', async () => {
    const data = { patientName: 'John Doe', age: 30 }
    await createCase(data)
    expect(request).toHaveBeenCalledWith({
      url: '/cases',
      method: 'post',
      data
    })
  })

  it('updateCase calls request with correct url, method and data', async () => {
    const caseId = 123
    const data = { patientName: 'Jane Doe' }
    await updateCase(caseId, data)
    expect(request).toHaveBeenCalledWith({
      url: `/cases/${caseId}`,
      method: 'put',
      data
    })
  })

  it('deleteCase calls request with correct url and method', async () => {
    const caseId = 123
    await deleteCase(caseId)
    expect(request).toHaveBeenCalledWith({
      url: `/cases/${caseId}`,
      method: 'delete'
    })
  })

  it('markTypical calls request with correct url, method and data', async () => {
    const caseId = 123
    const data = { isTypical: true }
    await markTypical(caseId, data)
    expect(request).toHaveBeenCalledWith({
      url: `/cases/${caseId}/typical`,
      method: 'post',
      data
    })
  })

  it('importCases calls request with FormData', async () => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' })
    await importCases(file)

    // Check that request was called once
    expect(request).toHaveBeenCalledTimes(1)

    // Get the arguments of the last call
    const callArgs = vi.mocked(request).mock.calls[0][0]

    expect(callArgs.url).toBe('/cases/import')
    expect(callArgs.method).toBe('post')
    expect(callArgs.data).toBeInstanceOf(FormData)
    expect(callArgs.data.get('file')).toBe(file)
    expect(callArgs.headers).toEqual({
      'Content-Type': 'multipart/form-data'
    })
  })
})
