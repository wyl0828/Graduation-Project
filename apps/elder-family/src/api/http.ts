export interface ApiResponse<T = any> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface ApiError extends Error {
  code?: string
  statusCode?: number
  details?: any
}

class UniHttpClient {
  // 小程序必须明确配置可从设备访问的 API 地址；H5 开发模式使用同源代理。
  private baseUrl = import.meta.env.VITE_API_BASE_URL || (typeof window !== 'undefined' ? '/api/v1' : '')

  private getAuthToken(): string {
    return uni.getStorageSync('token') || ''
  }

  request<T = any>(
    endpoint: string,
    method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
    data?: any,
    requestHeaders?: Record<string, string>,
  ): Promise<ApiResponse<T>> {
    if (!this.baseUrl && !endpoint.startsWith('http')) {
      const error: ApiError = new Error('未配置可访问的 API 地址（VITE_API_BASE_URL）')
      error.code = 'API_BASE_URL_MISSING'
      return Promise.reject(error)
    }
    const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint}`
    const token = this.getAuthToken()

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    if (requestHeaders) Object.assign(headers, requestHeaders)

    return new Promise((resolve, reject) => {
      uni.request({
        url,
        method,
        data,
        header: headers,
        success: (res) => {
          const respData = res.data as ApiResponse<T>
          if (res.statusCode >= 200 && res.statusCode < 300) {
            if (respData.code === 'OK' || respData.code === '200') {
              resolve(respData)
            } else {
              const err: ApiError = new Error(respData.message || '业务处理失败')
              err.code = respData.code
              err.statusCode = res.statusCode
              err.details = respData.data
              reject(err)
            }
          } else {
            if (res.statusCode === 401) {
              uni.removeStorageSync('token')
              uni.removeStorageSync('user')
              uni.removeStorageSync('activeRole')
              const current = getCurrentPages().at(-1)
              const hasIdempotencyKey = Object.keys(requestHeaders || {})
                .some(name => name.toLowerCase() === 'idempotency-key')
              if (!hasIdempotencyKey && current && current.route !== 'pages/auth/login') {
                uni.reLaunch({ url: '/pages/auth/login' })
              }
            }
            const err: ApiError = new Error(respData?.message || `网络错误 (${res.statusCode})`)
            err.code = respData?.code
            err.statusCode = res.statusCode
            err.details = respData?.data
            reject(err)
          }
        },
        fail: (err) => {
          const netErr: ApiError = new Error(err.errMsg || '网络连接不可用')
          netErr.code = 'NETWORK_ERROR'
          reject(netErr)
        },
      })
    })
  }

  get<T = any>(endpoint: string, params?: Record<string, any>, requestHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
    let url = endpoint
    if (params && typeof params === 'object') {
      const query = Object.entries(params)
        .filter(([_, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join('&')
      if (query) {
        url += (url.includes('?') ? '&' : '?') + query
      }
    }
    return this.request<T>(url, 'GET', undefined, requestHeaders)
  }

  post<T = any>(endpoint: string, data?: any, requestHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, 'POST', data, requestHeaders)
  }

  put<T = any>(endpoint: string, data?: any, requestHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, 'PUT', data, requestHeaders)
  }

  delete<T = any>(endpoint: string, data?: any, requestHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, 'DELETE', data, requestHeaders)
  }
}

export const uniHttp = new UniHttpClient()
