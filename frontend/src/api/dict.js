import request from '@/utils/request'

export const getDictItems = (dictCode) => request({ url: `/dict/${dictCode}/items`, method: 'get' })
