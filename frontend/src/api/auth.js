import request from '@/utils/request'

export const login = (data) => request({ url: '/auth/login', method: 'post', data })
export const logout = () => request({ url: '/auth/logout', method: 'post' })
export const refreshToken = (refreshToken) => request({ url: '/auth/refresh', method: 'post', data: { refreshToken } })
export const getMe = () => request({ url: '/auth/me', method: 'get' })
