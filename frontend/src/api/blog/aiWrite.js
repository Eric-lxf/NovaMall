import request from '@/utils/request'

/** 提交异步任务即可返回，短超时；实际 LLM 在后端后台执行 */
const SUBMIT_TIMEOUT_MS = 30000

export function generateTitles(data) {
  return request({
    url: '/blog/ai/write/titles',
    method: 'post',
    data,
    timeout: SUBMIT_TIMEOUT_MS,
  })
}

export function generateSummary(data) {
  return request({
    url: '/blog/ai/write/summary',
    method: 'post',
    data,
    timeout: SUBMIT_TIMEOUT_MS,
  })
}

export function generateOutline(data) {
  return request({
    url: '/blog/ai/write/outline',
    method: 'post',
    data,
    timeout: SUBMIT_TIMEOUT_MS,
  })
}

export function submitGenerateArticle(data) {
  return request({
    url: '/blog/ai/write/generate',
    method: 'post',
    data,
    timeout: SUBMIT_TIMEOUT_MS,
  })
}

export function fetchAiTask(id) {
  return request({
    url: `/blog/ai/tasks/${id}`,
    method: 'get',
    timeout: 15000,
  })
}
