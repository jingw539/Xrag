import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import { getPreviewToken } from '@/utils/preview'

const request = axios.create({
  baseURL: '/api',
  timeout: 150000,
  transformResponse: [data => {
    if (typeof data === 'string') {
      try {
        // Snowflake IDs (16+ digits) exceed JS MAX_SAFE_INTEGER.
        // Only quote bare numbers in JSON value positions (after ":" not inside strings).
        // Walk outside-string segments and replace there to avoid mangling string contents.
        const safeData = data.replace(/"(?:[^"\\]|\\.)*"|:\s*(\d{16,})/g, (match, digits) => {
          if (digits) return ': "' + digits + '"'
          return match
        })
        return JSON.parse(safeData)
      } catch { return data }
    }
    return data
  }]
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    const previewToken = getPreviewToken()
    if (previewToken) {
      config.headers['X-Preview-Token'] = previewToken
    }
    // Strip null/empty-string params so Spring's @DateTimeFormat binding doesn't fail
    if (config.params) {
      const cleaned = {}
      for (const [k, v] of Object.entries(config.params)) {
        if (v !== null && v !== undefined && v !== '') cleaned[k] = v
      }
      config.params = cleaned
    }
    return config
  },
  error => Promise.reject(error)
)

// 防止并发刷新
let refreshing = false
let waitQueue = []

const forceLogout = () => {
  const userStore = useUserStore()
  userStore.logout()
  router.push('/login')
}

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) return res
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async error => {
    const { response, config } = error
    
    // 处理超时错误
    if (error.code === 'ECONNABORTED' && error.message.includes('timeout')) {
      ElMessage.error('请求超时，AI服务处理时间较长，请稍后重试')
      return Promise.reject(error)
    }
    
    if (!response) {
      ElMessage.error('网络错误，请检查网络连接')
      return Promise.reject(error)
    }
    const previewToken = getPreviewToken()
    if (previewToken) {
      if (response.status === 403) {
        ElMessage.error('预览模式为只读，无法执行该操作')
      } else {
        ElMessage.error(response.data?.message || '请求失败')
      }
      return Promise.reject(error)
    }
    const status = response.status
    if (status === 401 && !config._retry) {
      const userStore = useUserStore()
      if (!userStore.refreshToken) { forceLogout(); return Promise.reject(error) }
      if (refreshing) {
        return new Promise((resolve, reject) =>
          waitQueue.push({ resolve, reject, config })
        )
      }
      refreshing = true
      config._retry = true
      try {
        const res = await axios.post('/api/auth/refresh', { refreshToken: userStore.refreshToken })
        const newToken = res.data?.data?.accessToken
        if (!newToken) throw new Error('no token')
        userStore.setToken(newToken)
        waitQueue.forEach(({ resolve, config: c }) => {
          c.headers.Authorization = `Bearer ${newToken}`
          resolve(request(c))
        })
        waitQueue = []
        config.headers.Authorization = `Bearer ${newToken}`
        return request(config)
      } catch (_) {
        waitQueue.forEach(({ reject }) => reject(_))
        waitQueue = []
        forceLogout()
        return Promise.reject(error)
      } finally { refreshing = false }
    }
    if (status === 403) ElMessage.error('拒绝访问')
    else if (status === 404) ElMessage.error('请求资源不存在')
    else if (status === 500) ElMessage.error('服务器错误')
    else if (status === 504) ElMessage.error('AI服务响应超时，请稍后重试')
    else ElMessage.error(response.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default request

