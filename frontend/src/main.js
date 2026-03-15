import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './styles/tokens.css'
import './styles/element-variables.css'
import './styles/base.css'
import App from './App.vue'
import router from './router'
import { elementPlusIcons } from './icons'
const app = createApp(App)
const pinia = createPinia()

if (import.meta.env.VITE_PERF_MODE === 'true') {
  document.documentElement.classList.add('perf-mode')
}

app.use(pinia)
app.use(router)
app.use(ElLoading)

Object.entries(elementPlusIcons).forEach(([name, component]) => {
  app.component(name, component)
})

app.mount('#app')
