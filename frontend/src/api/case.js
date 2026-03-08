import request from '@/utils/request'

// 查询病例列表
export const listCases = (params) => {
  return request({
    url: '/cases',
    method: 'get',
    params
  })
}

// 获取病例详情
export const getCaseById = (caseId) => {
  return request({
    url: `/cases/${caseId}`,
    method: 'get'
  })
}

export const claimCase = (caseId) => {
  return request({
    url: `/cases/${caseId}/claim`,
    method: 'post'
  })
}

// 创建病例
export const createCase = (data) => {
  return request({
    url: '/cases',
    method: 'post',
    data
  })
}

// 更新病例
export const updateCase = (caseId, data) => {
  return request({
    url: `/cases/${caseId}`,
    method: 'put',
    data
  })
}

// 删除病例
export const deleteCase = (caseId) => {
  return request({
    url: `/cases/${caseId}`,
    method: 'delete'
  })
}

// 标记/取消典型病例
export const markTypical = (caseId, data) => {
  return request({
    url: `/cases/${caseId}/typical`,
    method: 'post',
    data
  })
}

// 批量导入病例
export const importCases = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/cases/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
