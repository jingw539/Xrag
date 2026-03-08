import request from '@/utils/request'

export const listUsers = (params) => request({ url: '/users', method: 'get', params })
export const createUser = (data) => request({ url: '/users', method: 'post', data })
export const updateUser = (userId, data) => request({ url: `/users/${userId}`, method: 'put', data })
export const toggleUserStatus = (userId, status) => request({ url: `/users/${userId}/status`, method: 'put', data: { status } })
export const resetPassword = (userId, newPassword) => request({ url: `/users/${userId}/password`, method: 'put', data: { newPassword, oldPassword: '' } })
export const deleteUser = (userId) => request({ url: `/users/${userId}`, method: 'delete' })
