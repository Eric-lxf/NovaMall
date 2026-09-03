import request from '@/utils/request'

export const getExamCapabilities = () => request({ url: '/exam/capabilities', method: 'get' })
export const listExamTasks = params => request({ url: '/exam/tasks', method: 'get', params })
export const getExamTask = id => request({ url: `/exam/tasks/${encodeURIComponent(id)}`, method: 'get' })
export const createExamCheck = key => request({
  url: '/exam/tasks/check', method: 'post', data: { title: '命题模块基础自检' },
  headers: { 'Idempotency-Key': key, repeatSubmit: false }
})
export const cancelExamTask = (id, revision) => request({
  url: `/exam/tasks/${encodeURIComponent(id)}/cancel`, method: 'post', data: { expectedRevision: revision }
})
export const retryExamTask = (id, revision) => request({
  url: `/exam/tasks/${encodeURIComponent(id)}/retry`, method: 'post', data: { expectedRevision: revision }
})
