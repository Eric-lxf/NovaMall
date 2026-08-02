import request from '@/utils/request'

export function adjustMallInventory(data) {
  return request({
    url: '/mall/inventory/adjust',
    method: 'post',
    data
  })
}
