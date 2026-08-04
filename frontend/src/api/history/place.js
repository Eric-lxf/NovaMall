import request from '@/utils/request'

export function listHistoryPlace(params) {
  return request({ url: '/history/place', method: 'get', params })
}

export function listHistoryPlaceOptions() {
  return request({ url: '/history/place/options', method: 'get' })
}

export function getHistoryPlace(id) {
  return request({ url: `/history/place/${id}`, method: 'get' })
}

export function addHistoryPlace(data) {
  return request({ url: '/history/place', method: 'post', data })
}

export function updateHistoryPlace(data) {
  return request({ url: '/history/place', method: 'put', data })
}

export function delHistoryPlace(id) {
  return request({ url: `/history/place/${id}`, method: 'delete' })
}
