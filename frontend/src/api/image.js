import request from '@/utils/request'
import axios from 'axios'
import { useUserStore } from '@/stores/user'

export const uploadImage = (file, caseId, viewPosition, pixelSpacingXmm, pixelSpacingYmm) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('caseId', caseId)
  if (viewPosition) formData.append('viewPosition', viewPosition)
  if (pixelSpacingXmm !== undefined && pixelSpacingXmm !== null && pixelSpacingXmm !== '') formData.append('pixelSpacingXmm', pixelSpacingXmm)
  if (pixelSpacingYmm !== undefined && pixelSpacingYmm !== null && pixelSpacingYmm !== '') formData.append('pixelSpacingYmm', pixelSpacingYmm)
  return request({
    url: '/images/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const listImages = (caseId) => request({ url: '/images', method: 'get', params: { caseId } })
export const listPriorImages = (caseId, currentImageId) => request({ url: '/images/prior', method: 'get', params: { caseId, currentImageId } })
export const updateImageMetadata = (imageId, data) => request({ url: `/images/${imageId}/metadata`, method: 'put', data })
export const deleteImage = (imageId) => request({ url: `/images/${imageId}`, method: 'delete' })

export const fetchImageBlob = (imageId, thumbnail = false) => {
  const userStore = useUserStore()
  const headers = {}
  if (userStore.token) headers.Authorization = `Bearer ${userStore.token}`
  const suffix = thumbnail ? 'thumbnail' : 'content'
  return axios.get(`/api/images/${imageId}/${suffix}`, { responseType: 'blob', headers })
}
