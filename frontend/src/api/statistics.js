import request from '@/utils/request'

export const getOverview = (params = {}) => request({ url: '/statistics/overview', method: 'get', params })
export const getReportTrend = (params) => request({ url: '/statistics/report-trend', method: 'get', params })
