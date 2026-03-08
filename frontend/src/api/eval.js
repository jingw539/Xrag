import request from '@/utils/request'

export const triggerEval = (reportId) => request({ url: `/eval/reports/${reportId}`, method: 'post' })
export const getEvalByReportId = (reportId) => request({ url: `/eval/reports/${reportId}`, method: 'get' })
export const getEvalStatistics = () => request({ url: '/eval/statistics', method: 'get' })
