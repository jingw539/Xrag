import request from '@/utils/request'

// 查询操作日志
export const listLogs = (params) => {
  return request({
    url: '/logs',
    method: 'get',
    params
  })
}
