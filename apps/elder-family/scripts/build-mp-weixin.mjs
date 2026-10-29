import { spawn } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { normalizeApiBaseUrl } from './mp-api-env.mjs'

export function resolveBuildApiBaseUrl(explicitValue, environmentValue = process.env.VITE_API_BASE_URL) {
  return normalizeApiBaseUrl(explicitValue || environmentValue || '')
}

function runBuild() {
  let apiBaseUrl
  try {
    apiBaseUrl = resolveBuildApiBaseUrl(process.argv[2])
  } catch (error) {
    console.error(`[mp-weixin] 构建目标无效：${error.message}`)
    process.exitCode = 1
    return
  }

  const command = process.platform === 'win32' ? (process.env.ComSpec || 'cmd.exe') : 'npm'
  const commandArgs = process.platform === 'win32'
    ? ['/d', '/s', '/c', 'npm.cmd run build:mp-weixin']
    : ['run', 'build:mp-weixin']
  const child = spawn(command, commandArgs, {
    cwd: process.cwd(),
    env: { ...process.env, VITE_API_BASE_URL: apiBaseUrl },
    stdio: 'inherit',
  })

  child.on('error', (error) => {
    console.error(`[mp-weixin] 无法启动 npm：${error.message}`)
    process.exitCode = 1
  })
  child.on('exit', (code) => {
    process.exitCode = typeof code === 'number' ? code : 1
  })
}

const entryUrl = process.argv[1] ? pathToFileURL(process.argv[1]).href : ''
if (import.meta.url === entryUrl) runBuild()
