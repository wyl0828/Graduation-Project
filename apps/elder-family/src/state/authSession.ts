import { elderApi } from '../api/elderApi'
import { elderContext } from './elderContext'

function isUnauthorized(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const apiError = error as { code?: unknown; statusCode?: unknown }
  return apiError.statusCode === 401 || apiError.code === 'UNAUTHORIZED'
}

function clearCurrentSession(): void {
  uni.removeStorageSync('token')
  uni.removeStorageSync('user')
  uni.removeStorageSync('activeRole')
  elderContext.clear()
  uni.reLaunch({ url: '/pages/auth/login' })
}

export async function logoutCurrentSession(): Promise<void> {
  try {
    await elderApi.logout()
  } catch (error) {
    if (!isUnauthorized(error)) throw error
  }

  clearCurrentSession()
}
