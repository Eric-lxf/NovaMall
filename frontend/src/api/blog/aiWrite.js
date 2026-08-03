import request from '@/utils/request'

/** 智写同步 LLM 调用可能数分钟；须覆盖 axios 默认 10s，并与 Nginx proxy_read_timeout / OkHttp 对齐 */
const AI_WRITE_TIMEOUT_MS = 300000

export function generateTitles(data) {
  return request({
    url: '/blog/ai/write/titles',
    method: 'post',
    data,
    timeout: AI_WRITE_TIMEOUT_MS,
  })
}

export function generateSummary(data) {
  return request({
    url: '/blog/ai/write/summary',
    method: 'post',
    data,
    timeout: AI_WRITE_TIMEOUT_MS,
  })
}

export function generateOutline(data) {
  return request({
    url: '/blog/ai/write/outline',
    method: 'post',
    data,
    timeout: AI_WRITE_TIMEOUT_MS,
  })
}

export function submitGenerateArticle(data) {
  return request({
    url: '/blog/ai/write/generate',
    method: 'post',
    data,
    // 仅创建异步任务，保持较短超时即可
    timeout: 30000,
  })
}

export function fetchAiTask(id) {
  return request({
    url: `/blog/ai/tasks/${id}`,
    method: 'get',
  })
}
