import request from '@/utils/request'

export const getOverview = () => request({ url: '/statistics/overview', method: 'get' })
export const getReportTrend = (params) => request({ url: '/statistics/report-trend', method: 'get', params })
export const getEvalTrend = (params) => request({ url: '/statistics/eval-trend', method: 'get', params })
export const getModelComparison = () => request({ url: '/statistics/model-comparison', method: 'get' })
export const getPerLabelStats = () => request({ url: '/statistics/per-label-stats', method: 'get' })
export const getQualityIssues = (params) => request({ url: '/statistics/quality-issues', method: 'get', params })
