import request from '@/utils/request'

export const getOverview = () => request({ url: '/statistics/overview', method: 'get' })
export const getReportTrend = (params) => request({ url: '/statistics/report-trend', method: 'get', params })
