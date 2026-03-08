import request from '@/utils/request'

// 上传影像
export const uploadImage = (file, caseId, viewPosition) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('caseId', caseId)
  if (viewPosition) {
    formData.append('viewPosition', viewPosition)
  }
  return request({
    url: '/images/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 查询病例影像列表
export const listImages = (caseId) => {
  return request({
    url: '/images',
    method: 'get',
    params: { caseId }
  })
}

// 删除影像
export const deleteImage = (imageId) => {
  return request({
    url: `/images/${imageId}`,
    method: 'delete'
  })
}
