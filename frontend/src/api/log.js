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
  const response = await axios.get('/api/logs/export', {
    params,
    responseType: 'blob',
    headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}
  })
  return response.data
}
