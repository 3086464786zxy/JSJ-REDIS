import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { acknowledgeActivity, clearSession, isSessionIdle, pendingActivity } from './session'

const config: AxiosRequestConfig = { baseURL: '', timeout: 10000, withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }
export interface Result<T = any> { code: number; msg: string; data: T }
interface SessionRequest extends InternalAxiosRequestConfig { authRetried?: boolean; activityAt?: number }
type RefreshResult = { accessToken: string; idleTimeoutSeconds: number }
const publicEndpoints = new Set(['/api/sysUser/login', '/api/sysUser/getImage', '/api/refresh', '/api/sysUser/loginOut'])
let exiting = false

/** 清理 Pinia 和持久化数据，退出接口允许过期 JWT，并通过 Cookie 撤销会话。 */
export function endSession(message = '') {
  if (exiting) return
  exiting = true
  const token = useUserStore().getToken
  useUserStore().$reset()
  clearSession()
  sessionStorage.clear()
  if (message) ElMessage.warning(message)
  void axios.post('/api/sysUser/loginOut', undefined, {
    ...config, timeout: 3000, headers: { ...config.headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  }).catch(() => undefined).finally(() => window.location.replace('/login'))
}

class Http {
  private instance: AxiosInstance
  private refreshClient: AxiosInstance
  private refreshPromise: Promise<string> | null = null

  constructor(options: AxiosRequestConfig) {
    this.instance = axios.create(options)
    this.refreshClient = axios.create(options)
    this.instance.interceptors.request.use((request: SessionRequest) => {
      const token = useUserStore().getToken
      if (!publicEndpoints.has(request.url || '') && token) {
        if (isSessionIdle()) {
          endSession('长时间未操作，请重新登录')
          return Promise.reject(new Error('登录已失效'))
        }
        request.headers.set('Authorization', `Bearer ${token}`)
        request.activityAt = pendingActivity()
        if (request.activityAt) request.headers.set('X-Session-Activity', '1')
        else request.headers.delete('X-Session-Activity')
      }
      return request
    })
    this.instance.interceptors.response.use(async (response: AxiosResponse<Result>) => {
      if (response.data.code === 600 || response.data.code === 401) {
        return this.retryAfterRefresh(response.config as SessionRequest)
      }
      if (response.data.code !== 200) {
        ElMessage.error(response.data.msg || '服务器出错')
        return Promise.reject(new Error(response.data.msg || '服务器出错'))
      }
      acknowledgeActivity((response.config as SessionRequest).activityAt || 0)
      return response.data as unknown as AxiosResponse
    }, async (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.status === 401 && error.config) {
        if (publicEndpoints.has(error.config.url || '')) {
          ElMessage.error(error.response?.data?.msg || '账号、密码错误或账户不可用')
          return Promise.reject(error)
        }
        return this.retryAfterRefresh(error.config as SessionRequest)
      }
      const message = axios.isAxiosError(error)
        ? (error.response?.data?.msg || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '请求失败，请检查网络或联系管理员'))
        : '请求失败'
      if (!exiting) ElMessage.error(message)
      return Promise.reject(error)
    })
  }

  private async retryAfterRefresh(request: SessionRequest): Promise<AxiosResponse> {
    if (publicEndpoints.has(request.url || '')) {
      return Promise.reject(new Error('认证失败'))
    }
    if (request.authRetried || exiting || isSessionIdle() || !useUserStore().getToken) {
      endSession('登录已失效，请重新登录')
      return Promise.reject(new Error('登录已失效'))
    }
    request.authRetried = true
    const currentToken = useUserStore().getToken
    // 较晚返回的旧请求使用已换发的 Token，不再刷新。
    if (request.headers.get('Authorization') !== `Bearer ${currentToken}`) {
      return this.instance.request(request)
    }
    if (!this.refreshPromise) {
      const refresh = () => this.refreshAccessToken()
      // 标签页之间共享 Cookie，Web Locks 防止同时轮换同一刷新凭证。
      const operation = navigator.locks
        ? navigator.locks.request('itmk:token-refresh', refresh).then((token) => token)
        : refresh()
      this.refreshPromise = operation.finally(() => { this.refreshPromise = null })
    }
    await this.refreshPromise
    return this.instance.request(request)
  }

  private async refreshAccessToken(): Promise<string> {
    if (exiting || isSessionIdle()) throw new Error('登录已失效')
    try {
      const response = await this.refreshClient.post<Result<RefreshResult>>('/api/refresh')
      if (response.data.code === 401 || response.data.code === 600) {
        endSession('登录已失效，请重新登录')
        throw new Error(response.data.msg || '登录已失效')
      }
      if (response.data.code !== 200 || !response.data.data?.accessToken) {
        throw new Error(response.data.msg || '登录续期失败')
      }
      if (exiting || isSessionIdle() || !useUserStore().getToken) throw new Error('登录已失效')
      const token = response.data.data.accessToken
      useUserStore().setToken(token)
      return token
    } catch (error) {
      if (axios.isAxiosError(error) && (error.response?.status === 401 || error.response?.status === 403)) {
        endSession('登录已失效，请重新登录')
      } else if (!exiting) {
        ElMessage.error('登录续期失败，请检查网络后重试')
      }
      throw error
    }
  }

  get<T = Result>(url: string, params?: object): Promise<T> { return this.instance.get(url, { params }) as unknown as Promise<T> }
  post<T = Result>(url: string, data?: object): Promise<T> { return this.instance.post(url, data) as unknown as Promise<T> }
  put<T = Result>(url: string, data?: object): Promise<T> { return this.instance.put(url, data) as unknown as Promise<T> }
  delete<T = Result>(url: string): Promise<T> { return this.instance.delete(url) as unknown as Promise<T> }
  upload<T = Result>(url: string, params?: object): Promise<T> { return this.instance.post(url, params) as unknown as Promise<T> }
}
export default new Http(config)
