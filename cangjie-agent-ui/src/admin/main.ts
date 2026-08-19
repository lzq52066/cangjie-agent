import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { hasPerm } from '@shared/utils/permission'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

// 权限指令：v-permission="'code'" 或 v-permission="['code1','code2']"
app.directive('permission', {
  mounted(el: HTMLElement, binding: { value: string | string[] }) {
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!codes.some(c => hasPerm(c))) {
      el.remove()
    }
  }
})

app.use(ElementPlus)
app.use(createPinia())
app.use(router)
app.mount('#app')
