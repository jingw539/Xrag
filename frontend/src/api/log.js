import axios from 'axios'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { getPreviewToken } from '@/utils/preview'

export const listLogs = (params) => {
  return request({
    url: '/logs',
    method: 'get',
    params
  })
}

export const exportLogsFile = async (params) => {
  const userStore = useUserStore()
  const previewToken = getPreviewToken()
  const headers = {}
  if (userStore.token) headers.Authorization = `Bearer ${userStore.token}`
  if (previewToken) headers['X-Preview-Token'] = previewToken
  const response = await axios.get('/api/logs/export', {
    params,
    responseType: 'blob',
    headers
  })
  return response.data
}
