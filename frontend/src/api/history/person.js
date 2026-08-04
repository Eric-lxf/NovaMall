import request from '@/utils/request'

export function listHistoryPerson(params) {
  return request({ url: '/history/person', method: 'get', params })
}

export function getHistoryPerson(id) {
  return request({ url: `/history/person/${id}`, method: 'get' })
}

export function addHistoryPerson(data) {
  return request({ url: '/history/person', method: 'post', data })
}

export function updateHistoryPerson(data) {
  return request({ url: '/history/person', method: 'put', data })
}

export function delHistoryPerson(id) {
  return request({ url: `/history/person/${id}`, method: 'delete' })
}
