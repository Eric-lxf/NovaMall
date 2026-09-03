import request from '@/utils/request'
import { saveAs } from 'file-saver'

// Do not persist private source/question request bodies in the shared request-layer session cache.
export const examGet = (path, params) => request({ url: `/exam/${path}`, method: 'get', params })
export const examWrite = (path, data, method = 'post', key) => request({
  url: `/exam/${path}`, method, data, timeout: 30000,
  headers: { repeatSubmit: false, ...(key ? { 'Idempotency-Key': key } : {}) }
})
export const examUpload = (path, data, key) => request({
  url: `/exam/${path}`, method: 'post', data, timeout: 60000,
  headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false, ...(key ? { 'Idempotency-Key': key } : {}) }
})
export async function examDownload(path, filename) {
  const blob = await request({ url: `/exam/${path}`, method: 'get', responseType: 'blob', timeout: 60000 })
  if (blob.type.includes('json')) { const failure = JSON.parse(await blob.text()); throw new Error(failure.msg || '下载失败') }
  saveAs(blob, filename)
}
