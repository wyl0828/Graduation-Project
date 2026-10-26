export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface ApiError extends Error {
  status?: number
  code?: string
  details?: unknown
}

class HttpClient {
  private baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'

  async request<T = unknown>(endpoint: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
    const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint}`
    const headers = new Headers(options.headers || {})

    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    const csrfToken = localStorage.getItem('csrfToken')
    if (csrfToken && options.method && !['GET', 'HEAD'].includes(options.method.toUpperCase()) && !headers.has('X-CSRF-Token')) {
      headers.set('X-CSRF-Token', csrfToken)
    }

    const response = await fetch(url, {
      ...options,
      headers,
      credentials: 'include',
    })

    if (response.status === 401) {
      localStorage.removeItem('user')
      localStorage.removeItem('csrfToken')
      localStorage.removeItem('activeRole')
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    const result: ApiResponse<T> = await response.json().catch(() => ({
      code: 'INVALID_RESPONSE',
      message: `服务端响应无法解析 (${response.status})`,
      data: null as T,
    }))

    if (!response.ok || (result.code && result.code !== 'OK' && result.code !== '200')) {
      const errorMsg = result.message || `请求失败 (${response.status})`
      const error = new Error(errorMsg) as ApiError
      error.status = response.status
      error.code = result.code
      error.details = result.data
      throw error
    }

    return result
  }

  get<T = unknown>(endpoint: string, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'GET', headers })
  }

  post<T = unknown>(endpoint: string, body?: unknown, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'POST',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  put<T = unknown>(endpoint: string, body?: unknown, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  delete<T = unknown>(endpoint: string, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'DELETE', headers })
  }
}

export const http = new HttpClient()
