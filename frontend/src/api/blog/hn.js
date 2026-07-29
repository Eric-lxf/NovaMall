import request from '@/utils/request'

export function fetchHnItemPage(params) {
  return request({ url: '/blog/hn/items', method: 'get', params })
}
export function fetchHnSyncStatus() {
  return request({ url: '/blog/hn/sync/status', method: 'get' })
}
export function syncHnBoard(board, data = {}) {
  return request({ url: `/blog/hn/sync/${board}`, method: 'post', data })
}
export function syncHnAll(data = {}) {
  return request({ url: '/blog/hn/sync', method: 'post', data })
}
