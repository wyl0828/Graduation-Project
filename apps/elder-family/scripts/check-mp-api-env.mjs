import { normalizeApiBaseUrl } from './mp-api-env.mjs'

try {
  const apiBaseUrl = normalizeApiBaseUrl(process.env.VITE_API_BASE_URL)
  console.log(`[mp-weixin] VITE_API_BASE_URL 已配置：${apiBaseUrl}`)
} catch (error) {
  console.error(`[mp-weixin] 构建前检查失败：${error.message}`)
  console.error('[mp-weixin] PowerShell 示例：$env:VITE_API_BASE_URL="https://api.example.test/api/v1"')
  process.exitCode = 1
}
