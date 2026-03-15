import axios from 'axios'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

export const listLogs = (params) => {
  return request({
    url: '/logs',
    method: 'get',
    params
  })
}

export const exportLogsFile = async (params) => {
  const userStore = useUserStore()
  const headers = {}
  if (userStore.token) headers.Authorization = `Bearer ${userStore.token}`
  const response = await axios.get('/api/logs/export', {
    params,
    responseType: 'blob',
    headers
  })
  return response.data
}
