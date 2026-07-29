import request from '@/utils/request'

const noToken = { isToken: false }

export function fetchPublicHnBoardItems(board, params) {
  return request({
    url: `/public/blog/hn/boards/${board}/items`,
    method: 'get',
    params,
    headers: noToken,
  })
}

export function fetchPublicHnItem(hnId) {
  return request({
    url: `/public/blog/hn/items/${hnId}`,
    method: 'get',
    headers: noToken,
  })
}
