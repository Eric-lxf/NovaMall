import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Workbench from '../src/views/exam/workbench/index.vue'
const router = createRouter({ history: createWebHashHistory(), routes: [{ path: '/:pathMatch(.*)*', component: Workbench }] })
const app = createApp(Workbench).use(router).use(ElementPlus)
app.directive('hasPermi', {})
await router.isReady()
app.mount('#app')
