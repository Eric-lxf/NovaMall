import request from '@/utils/request'

export function listHistorySource(params) {
  return request({ url: '/history/source', method: 'get', params })
}

export function getHistorySource(id, includeFragments = false) {
  return request({
    url: `/history/source/${id}`,
    method: 'get',
    params: { includeFragments }
  })
}

export function listHistorySourceFragments(documentId) {
  return request({ url: `/history/source/${documentId}/fragments`, method: 'get' })
}

export function getHistoryFragment(fragmentId) {
  return request({ url: `/history/fragments/${fragmentId}`, method: 'get' })
}

export function importHistorySource(data) {
  return request({ url: '/history/source/import', method: 'post', data })
}

export function listHistoryTask(params) {
  return request({ url: '/history/tasks', method: 'get', params })
}

export function getHistoryTask(taskId) {
  return request({ url: `/history/tasks/${taskId}`, method: 'get' })
}

export function retryHistoryTask(taskId) {
  return request({ url: `/history/tasks/${taskId}/retry`, method: 'post' })
}

export function extractHistorySource(documentId) {
  return request({ url: '/history/source/extract', method: 'post', data: { documentId } })
}

export function listHistoryClaims(params) {
  return request({ url: '/history/claims', method: 'get', params })
}

export function auditHistoryClaims(data) {
  return request({ url: '/history/claims/audit', method: 'post', data })
}

export function getHistoryTimeline(params) {
  return request({ url: '/history/timeline', method: 'get', params })
}

export function getPublicHistoryEvent(id) {
  return request({ url: `/history/events/${id}/public`, method: 'get' })
}

export function listPublicHistoryPaths(params) {
  return request({ url: '/history/paths/public', method: 'get', params })
}

export function getPublicHistoryPath(id) {
  return request({ url: `/history/paths/${id}/public`, method: 'get' })
}

export function getPublicHistoryUnit(id) {
  return request({ url: `/history/units/${id}/public`, method: 'get' })
}

export function saveHistoryProgress(data) {
  return request({ url: '/history/learning/progress', method: 'post', data })
}

/** 上传 PDF 等到通用上传接口，返回 fileName（/profile/...） */
export function uploadHistoryFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/common/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
