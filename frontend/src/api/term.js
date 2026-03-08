import request from '@/utils/request'

export const analyzeTerms = (reportId, data) => request({ url: `/terms/reports/${reportId}/analyze`, method: 'post', data })
export const getTermsByReportId = (reportId) => request({ url: `/terms/reports/${reportId}`, method: 'get' })
export const acceptCorrection = (correctionId) => request({ url: `/terms/${correctionId}/accept`, method: 'post' })
export const dismissCorrection = (correctionId) => request({ url: `/terms/${correctionId}/dismiss`, method: 'post' })
