import request from '@/utils/request'

export const generateReport = (data) => request({ url: '/reports/generate', method: 'post', data })
export const regenerateReport = (reportId) => request({ url: `/reports/${reportId}/regenerate`, method: 'post' })
export const saveDraft = (reportId, data) => request({ url: `/reports/${reportId}/draft`, method: 'put', data })
export const signReport = (reportId) => request({ url: `/reports/${reportId}/sign`, method: 'post' })
export const getReport = (reportId) => request({ url: `/reports/${reportId}`, method: 'get' })
export const listReports = (params) => request({ url: '/reports', method: 'get', params })
export const getEditHistory = (reportId) => request({ url: `/reports/${reportId}/history`, method: 'get' })
export const polishReport = (data) => request({ url: '/reports/polish', method: 'post', data })
export const revertReport = (reportId) => request({ url: `/reports/${reportId}/revert`, method: 'post' })
export const getAiAdvice = (reportId) => request({ url: `/reports/${reportId}/ai-advice`, method: 'post' })
