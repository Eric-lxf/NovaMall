import request from '@/utils/request'

export function listHistoryEvent(params) {
  return request({ url: '/history/events', method: 'get', params })
}

export function getHistoryEvent(id) {
  return request({ url: `/history/events/${id}`, method: 'get' })
}

export function addHistoryEvent(data) {
  return request({ url: '/history/events', method: 'post', data })
}

export function updateHistoryEvent(data) {
  return request({ url: '/history/events', method: 'put', data })
}

export function delHistoryEvent(id) {
  return request({ url: `/history/events/${id}`, method: 'delete' })
}

export function publishHistoryEvent(id) {
  return request({ url: `/history/events/${id}/publish`, method: 'post' })
}
