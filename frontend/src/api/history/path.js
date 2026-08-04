import request from '@/utils/request'

export function listHistoryPath(params) {
  return request({ url: '/history/paths', method: 'get', params })
}

export function getHistoryPath(id) {
  return request({ url: `/history/paths/${id}`, method: 'get' })
}

export function addHistoryPath(data) {
  return request({ url: '/history/paths', method: 'post', data })
}

export function updateHistoryPath(data) {
  return request({ url: '/history/paths', method: 'put', data })
}

export function delHistoryPath(id) {
  return request({ url: `/history/paths/${id}`, method: 'delete' })
}

export function publishHistoryPath(id) {
  return request({ url: `/history/paths/${id}/publish`, method: 'post' })
}
