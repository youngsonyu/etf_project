import axios from 'axios'
import { ElMessage } from 'element-plus'

function parseTimeout(value, fallback) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export const DEFAULT_API_TIMEOUT = parseTimeout(import.meta.env.VITE_API_TIMEOUT, 30000)
export const LONG_TASK_TIMEOUT = parseTimeout(import.meta.env.VITE_LONG_TASK_TIMEOUT, 180000)
export const VERY_LONG_TASK_TIMEOUT = parseTimeout(import.meta.env.VITE_VERY_LONG_TASK_TIMEOUT, 900000)

const service = axios.create({
  baseURL: '/',
  timeout: DEFAULT_API_TIMEOUT
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
