import request from '@/utils/request'

export const listConfigs = () => request({ url: '/config', method: 'get' })
export const getConfigByKey = (configKey) => request({ url: `/config/${configKey}`, method: 'get' })
export const updateConfig = (configKey, configValue) => request({ url: `/config/${configKey}`, method: 'put', data: { configValue } })
