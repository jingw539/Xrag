import request from '@/utils/request'

export const listEvaluationRuns = (params) => request({ url: '/evaluations/runs', method: 'get', params })
export const getEvaluationMetrics = (runId, params) => request({ url: `/evaluations/runs/${runId}/metrics`, method: 'get', params })
export const compareEvaluationModels = (params) => request({ url: '/evaluations/compare', method: 'get', params })
