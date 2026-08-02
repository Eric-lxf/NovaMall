/**
 * 上传资源（/profile、/uploads 等）解析。
 * 优先 VITE_APP_UPLOAD_ORIGIN；开发环境回退 VITE_APP_BASE_API（如 /dev-api），
 * 与 ImageUpload / ImagePreview 一致，避免列表页图片请求打到前端端口导致不回显。
 */
const UPLOAD_ORIGIN = (import.meta.env.VITE_APP_UPLOAD_ORIGIN || '').replace(/\/$/, '')
const BASE_API = (import.meta.env.VITE_APP_BASE_API || '').replace(/\/$/, '')

export function resolveUploadUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('blob:') || url.startsWith('data:')) return url
  const prefix = UPLOAD_ORIGIN || BASE_API
  const path = url.startsWith('/') ? url : `/${url}`
  if (!prefix) return path
  if (url.startsWith(prefix + '/') || url === prefix) return url
  return `${prefix}${path}`
}

export function resolveMarkdownAssets(markdown) {
  if (!markdown) return ''
  if (!UPLOAD_ORIGIN) return markdown
  const origin = UPLOAD_ORIGIN
  return markdown
    .replace(/\]\(\/uploads\//g, `](${origin}/uploads/`)
    .replace(/src="\/uploads\//g, `src="${origin}/uploads/`)
}
