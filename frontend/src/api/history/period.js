import request from '@/utils/request'

export function listHistoryPeriod(params) {
  return request({ url: '/history/period', method: 'get', params })
}

export function listHistoryPeriodOptions() {
  return request({ url: '/history/period/options', method: 'get' })
}

export function getHistoryPeriod(id) {
  return request({ url: `/history/period/${id}`, method: 'get' })
}

export function addHistoryPeriod(data) {
  return request({ url: '/history/period', method: 'post', data })
}

export function updateHistoryPeriod(data) {
  return request({ url: '/history/period', method: 'put', data })
}

export function delHistoryPeriod(id) {
  return request({ url: `/history/period/${id}`, method: 'delete' })
}
