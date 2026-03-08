import request from '@/utils/request'

export const searchRetrieval = (caseId, imageId, topK = 3) =>
  request({ url: '/retrieval/search', method: 'post', params: { caseId, imageId, topK } })
export const getRetrieval = (retrievalId) => request({ url: `/retrieval/${retrievalId}`, method: 'get' })
export const listRetrievalByCaseId = (caseId) => request({ url: `/retrieval/case/${caseId}`, method: 'get' })
