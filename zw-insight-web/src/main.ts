import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { EP_ICON_MAP } from './components/icons/registry'

import App from './App.vue'
import router from './router'
import { permissionDirective } from './utils/permission'

// 字体（自托管子集，零 CDN）：Display 层 Barlow Condensed（D-DIN 开源替代）+ 账本数字 JetBrains Mono；中文零 webfont 走系统栈
import '@fontsource/barlow-condensed/latin-700.css'
import '@fontsource/jetbrains-mono/latin-400.css'
import '@fontsource/jetbrains-mono/latin-500.css'
import '@fontsource/jetbrains-mono/latin-700.css'

// Design System - 必须在 Element Plus 样式之后引入
import './styles/tokens/base.css'
import './styles/tokens/light.css'
import './styles/tokens/dark.css'
import './styles/element-override.scss'
import './styles/global.scss'

const app = createApp(App)

// Pinia
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)

// Router
app.use(router)

// Element Plus
app.use(ElementPlus, { locale: zhCn })

// 注册图标：存量名称经 EP→Tabler 映射层统一切换底层实现（模板/路由/菜单字符串零改动）
for (const [key, component] of Object.entries(EP_ICON_MAP)) {
  app.component(key, component)
}

// 注册全局权限指令
app.directive('permission', permissionDirective)

app.mount('#app')
