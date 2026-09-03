import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Tasks from '../src/views/exam/task/index.vue'
import AiConfirm from '../src/views/exam/workbench/AiConfirm.vue'
const extraction = new URLSearchParams(location.search).get('mode') === 'extract'
const app = createApp({ render: () => extraction ? h(AiConfirm, { operation: 'extract', payload: { sourceVersionId: '11' } }) : h(Tasks) }).use(ElementPlus)
app.directive('hasPermi', {})
app.component('pagination', { template: '<div>离线测试分页</div>' })
app.mount('#app')
