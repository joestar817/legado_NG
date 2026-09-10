import { createApp } from 'vue'
import App from '@/App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { bookRoutes } from '@/router/bookRouter'
import store from '@/store'
import 'element-plus/theme-chalk/dark/css-vars.css'

const bookRouter = createRouter({
  history: createWebHashHistory(),
  routes: bookRoutes,
})

createApp(App).use(store).use(bookRouter).mount('#app')

// 同步Element PLUS 夜间模式
watch(
  () => useBookStore().isNight,
  isNight => {
    if (isNight) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  },
)
