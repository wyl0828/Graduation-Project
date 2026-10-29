import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeApiBaseUrl } from './mp-api-env.mjs'
import { resolveBuildApiBaseUrl } from './build-mp-weixin.mjs'

test('缺少 VITE_API_BASE_URL 时拒绝构建配置', () => {
  assert.throws(
    () => normalizeApiBaseUrl(''),
    /VITE_API_BASE_URL.*绝对 API 地址/,
  )
})

test('接受绝对 API 地址并去掉末尾斜杠', () => {
  assert.equal(
    normalizeApiBaseUrl('https://api.example.test/api/v1///'),
    'https://api.example.test/api/v1',
  )
})

test('拒绝相对地址或不包含 API 版本路径的地址', () => {
  assert.throws(
    () => normalizeApiBaseUrl('/api/v1'),
    /绝对 API 地址/,
  )
  assert.throws(
    () => normalizeApiBaseUrl('https://api.example.test'),
    /必须包含 \/api\/v1 路径/,
  )
})

test('ECS 构建入口优先使用明确的 ECS 地址', () => {
  assert.equal(
    resolveBuildApiBaseUrl(
      'https://aimianshi.xyz/api/v1',
      'http://127.0.0.1:8080/api/v1',
    ),
    'https://aimianshi.xyz/api/v1',
  )
})
