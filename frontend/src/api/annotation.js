import request from '@/utils/request'

export const listAnnotations = (imageId) =>
  request({ url: `/annotations/image/${imageId}`, method: 'get' })

export const createAnnotation = (data) =>
  request({ url: '/annotations', method: 'post', data })

export const updateAnnotation = (id, data) =>
  request({ url: `/annotations/${id}`, method: 'put', data })

export const deleteAnnotation = (id) =>
  request({ url: `/annotations/${id}`, method: 'delete' })
