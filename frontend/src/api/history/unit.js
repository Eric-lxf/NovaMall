import request from '@/utils/request'

export function listHistoryUnit(params) {
  return request({ url: '/history/units', method: 'get', params })
}

export function listHistoryUnitOptions() {
  return request({ url: '/history/units/options', method: 'get' })
}

export function getHistoryUnit(id) {
  return request({ url: `/history/units/${id}`, method: 'get' })
}

export function addHistoryUnit(data) {
  return request({ url: '/history/units', method: 'post', data })
}

export function updateHistoryUnit(data) {
  return request({ url: '/history/units', method: 'put', data })
}

export function delHistoryUnit(id) {
  return request({ url: `/history/units/${id}`, method: 'delete' })
}

export function publishHistoryUnit(id) {
  return request({ url: `/history/units/${id}/publish`, method: 'post' })
}

export function generateHistoryUnitFromEvent(eventId) {
  return request({ url: '/history/units/generate-from-event', method: 'post', data: { eventId } })
}
