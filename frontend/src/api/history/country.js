import request from '@/utils/request'

export function listHistoryCountry(params) {
  return request({ url: '/history/countries', method: 'get', params })
}

export function listHistoryCountryOptions() {
  return request({ url: '/history/countries/options', method: 'get' })
}

export function getHistoryCountry(id) {
  return request({ url: `/history/countries/${id}`, method: 'get' })
}

export function addHistoryCountry(data) {
  return request({ url: '/history/countries', method: 'post', data })
}

export function updateHistoryCountry(data) {
  return request({ url: '/history/countries', method: 'put', data })
}

export function delHistoryCountry(id) {
  return request({ url: `/history/countries/${id}`, method: 'delete' })
}
