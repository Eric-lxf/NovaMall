import request from '@/utils/request'

// 分页查询激活码与批次统计汇总
export function listRedeemCodes(query) {
  return request({
    url: '/wedding/admin/redeem-codes',
    method: 'get',
    params: query
  })
}

// 按批次批量生成6位激活码
export function generateRedeemBatch(data) {
  return request({
    url: '/wedding/admin/redeem-codes/batch',
    method: 'post',
    data
  })
}

// 作废单枚未使用激活码
export function voidRedeemCode(code) {
  return request({
    url: `/wedding/admin/redeem-codes/${encodeURIComponent(code)}/void`,
    method: 'post'
  })
}

// 分页查询小程序微信用户列表
export function listWxUsers(query) {
  return request({
    url: '/wedding/admin/wx-users',
    method: 'get',
    params: query
  })
}

// 更新微信用户账号状态或运营备注
export function updateWxUser(openid, data) {
  return request({
    url: `/wedding/admin/wx-users/${encodeURIComponent(openid)}`,
    method: 'put',
    data
  })
}

// 查询单个婚礼空间运营详情
export function getWeddingAdminDetail(weddingId) {
  return request({
    url: `/wedding/admin/weddings/${encodeURIComponent(weddingId)}`,
    method: 'get'
  })
}
