import request from '@/utils/request'

export function listExternalApiClients(query) {
  return request({
    url: '/blog/external-api/clients',
    method: 'get',
    params: query
  })
}

export function createExternalApiClient(data) {
  return request({
    url: '/blog/external-api/clients',
    method: 'post',
    data
  })
}

export function updateExternalApiClientStatus(id, status) {
  return request({
    url: `/blog/external-api/clients/${id}/status`,
    method: 'put',
    data: { status }
  })
}

export function rotateExternalApiClientSecret(id) {
  return request({
    url: `/blog/external-api/clients/${id}/rotate-secret`,
    method: 'post'
  })
}
