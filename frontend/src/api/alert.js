import request from '@/utils/request'

export const listAlerts = (params) => request({ url: '/alerts', method: 'get', params })
export const getAlert = (alertId) => request({ url: `/alerts/${alertId}`, method: 'get' })
export const respondAlert = (alertId, data) => request({ url: `/alerts/${alertId}/respond`, method: 'post', data })
export const getAlertsByCaseId = (caseId) => request({ url: `/alerts/case/${caseId}`, method: 'get' })
export const getPendingCount = () => request({ url: '/alerts/pending/count', method: 'get' })
export const getAlertStatistics = () => request({ url: '/alerts/statistics', method: 'get' })
