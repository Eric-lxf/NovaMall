import auth from '@/plugins/auth'
import router, { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'

// 匹配views里面所有的.vue文件
const modules = import.meta.glob('./../../views/**/*.vue')

/** 动态菜单组件兜底（避免 glob 未命中或库中 component 路径不一致时白屏） */
const viewFallback = {
  'blog/dashboard/index': () => import('@/views/blog/dashboard/index.vue'),
  'blog/list/index': () => import('@/views/blog/list/index.vue'),
  'blog/comment/index': () => import('@/views/blog/comment/index.vue'),
  'blog/comment/report': () => import('@/views/blog/comment/report.vue'),
  'blog/comment/sensitive': () => import('@/views/blog/comment/sensitive.vue'),
  'blog/notification/index': () => import('@/views/blog/notification/index.vue'),
  'blog/notification/send': () => import('@/views/blog/notification/send.vue'),
  'blog/ai/write': () => import('@/views/blog/ai/write.vue'),
  'blog/ai/optimize': () => import('@/views/blog/ai/optimize.vue'),
  'blog/ai/provider/index': () => import('@/views/blog/ai/provider/index.vue'),
  'blog/dashboard/index': () => import('@/views/blog/dashboard/index.vue'),
  'wechat/account/index': () => import('@/views/wechat/account/index.vue'),
  'wechat/publish/index': () => import('@/views/wechat/publish/index.vue'),
  'wechat/material/index': () => import('@/views/wechat/material/index.vue'),
  'wechat/draft/index': () => import('@/views/wechat/draft/index.vue'),
  'wechat/menu/index': () => import('@/views/wechat/menu/index.vue'),
  'wechat/reply/index': () => import('@/views/wechat/reply/index.vue'),
  'wechat/fans/index': () => import('@/views/wechat/fans/index.vue'),
  'wechat/message/index': () => import('@/views/wechat/message/index.vue'),
  'wechat/qrcode/index': () => import('@/views/wechat/qrcode/index.vue'),
  'wechat/config/index': () => import('@/views/wechat/config/index.vue'),
  'wechat/tag/index': () => import('@/views/wechat/tag/index.vue'),
  'wechat/kefu/index': () => import('@/views/wechat/kefu/index.vue'),
  'wechat/template/index': () => import('@/views/wechat/template/index.vue'),
  'wechat/mass/index': () => import('@/views/wechat/mass/index.vue'),
  'mall/admin/category/index': () => import('@/views/mall/admin/category/index.vue'),
  'mall/admin/brand/index': () => import('@/views/mall/admin/brand/index.vue'),
  'mall/admin/spu/index': () => import('@/views/mall/admin/spu/index.vue'),
  'mall/admin/order/index': () => import('@/views/mall/admin/order/index.vue'),
  'mall/admin/payment/index': () => import('@/views/mall/admin/payment/index.vue'),
  'system/menu/index': () => import('@/views/system/menu/index.vue'),
}

function normalizeViewPath(view) {
  return String(view).replace(/\.vue$/i, '').replace(/^\/+/, '')
}

function buildViewCandidates(view) {
  const normalized = normalizeViewPath(view)
  const candidates = [normalized]
  if (!normalized.endsWith('/index')) {
    candidates.push(`${normalized}/index`)
  } else {
    candidates.push(normalized.replace(/\/index$/, ''))
  }
  return [...new Set(candidates)]
}

function dedupeRouteNames(routes, usedNames = new Set()) {
  routes.forEach(route => {
    if (route.name) {
      let name = String(route.name)
      if (usedNames.has(name)) {
        const suffix = String(route.path || '')
          .split('/')
          .filter(Boolean)
          .map(part => part.charAt(0).toUpperCase() + part.slice(1))
          .join('')
        name = `${name}${suffix || 'Route'}`
        route.name = name
      }
      usedNames.add(name)
    }
    if (route.children?.length) {
      dedupeRouteNames(route.children, usedNames)
    }
  })
}

const usePermissionStore = defineStore(
  'permission',
  {
    state: () => ({
      routes: [],
      addRoutes: [],
      defaultRoutes: [],
      topbarRouters: [],
      sidebarRouters: []
    }),
    actions: {
      setRoutes(routes) {
        this.addRoutes = routes
        this.routes = constantRoutes.concat(routes)
      },
      setDefaultRoutes(routes) {
        this.defaultRoutes = constantRoutes.concat(routes)
      },
      setTopbarRoutes(routes) {
        this.topbarRouters = routes
      },
      setSidebarRouters(routes) {
        this.sidebarRouters = routes
      },
      generateRoutes(roles) {
        return new Promise(resolve => {
          // 向后端请求路由数据
          getRouters().then(res => {
            const sdata = JSON.parse(JSON.stringify(res.data))
            const rdata = JSON.parse(JSON.stringify(res.data))
            const defaultData = JSON.parse(JSON.stringify(res.data))
            const sidebarRoutes = filterAsyncRouter(sdata)
            const rewriteRoutes = filterAsyncRouter(rdata, false, true)
            const defaultRoutes = filterAsyncRouter(defaultData)
            dedupeRouteNames(rewriteRoutes)
            const asyncRoutes = filterDynamicRoutes(dynamicRoutes)
            asyncRoutes.forEach(route => { router.addRoute(route) })
            this.setRoutes(rewriteRoutes)
            this.setSidebarRouters(constantRoutes.concat(sidebarRoutes))
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(defaultRoutes)
            resolve(rewriteRoutes)
          })
        })
      }
    }
  })

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  return asyncRouterMap.filter(route => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (route.component) {
      // Layout ParentView 组件特殊处理
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        const loaded = loadView(route.component)
        if (!loaded) {
          console.warn('[loadView] component not found:', route.component, route.path)
        }
        route.component = loaded
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap, lastRouter = false) {
  var children = []
  childrenMap.forEach(el => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

export const loadView = (view) => {
  if (!view) {
    return undefined
  }
  for (const candidate of buildViewCandidates(view)) {
    if (viewFallback[candidate]) {
      return viewFallback[candidate]
    }
    for (const path in modules) {
      const dir = path.split('views/')[1].split('.vue')[0]
      if (dir === candidate || dir.toLowerCase() === candidate.toLowerCase()) {
        return () => modules[path]()
      }
    }
  }
  console.warn('[loadView] component not found:', view)
  return undefined
}

export default usePermissionStore
