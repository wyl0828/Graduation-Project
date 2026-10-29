const API_PATH = '/api/v1'

export function normalizeApiBaseUrl(value) {
  const raw = typeof value === 'string' ? value.trim() : ''
  if (!raw) {
    throw new Error('VITE_API_BASE_URL 必须配置为绝对 API 地址，例如 https://api.example.test/api/v1')
  }

  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    throw new Error('VITE_API_BASE_URL 必须配置为绝对 API 地址，例如 https://api.example.test/api/v1')
  }

  if (!['http:', 'https:'].includes(parsed.protocol) || !parsed.hostname) {
    throw new Error('VITE_API_BASE_URL 必须使用 http 或 https 协议')
  }

  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('VITE_API_BASE_URL 不应包含账号、密码、查询参数或片段')
  }

  const pathname = parsed.pathname.replace(/\/+$/, '')
  if (!pathname.endsWith(API_PATH)) {
    throw new Error(`VITE_API_BASE_URL 必须包含 ${API_PATH} 路径`)
  }

  return `${parsed.origin}${pathname}`
}
