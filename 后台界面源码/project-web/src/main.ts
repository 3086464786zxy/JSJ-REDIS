//import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
//pinia持久化
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import myconfirm from './utils/myconfirm'
//按钮权限指令
//import { permission } from './directive/permission'
import hasPerm from './directive/hasPerm'
//权限验证
import './permission'
//echarts
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import http, { endSession } from './http'
import { installSessionActivity } from './http/session'
import { useUserStore } from './store/user'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(ElementPlus, {
  locale: zhCn,
})

//app.directive('permission',permission)
//app.config.globalProperties.$hasPerm = hasPerm
//app.use(ElementPlus)
app.use(pinia)
installSessionActivity(useUserStore().getToken,
  () => http.post('/api/session/activity'),
  () => endSession('长时间未操作，请重新登录'))
app.use(router)

app.mount('#app')
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

//全局属性的使用
app.config.globalProperties.$myconfirm = myconfirm;
app.config.globalProperties.$hasPerm = hasPerm
app.config.globalProperties.$echarts = echarts;
