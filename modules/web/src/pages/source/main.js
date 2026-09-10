import { createApp } from 'vue'
import App from '@/App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { sourceRoutes } from '@/router/sourceRouter'
import store from '@/store'
import 'element-plus/theme-chalk/dark/css-vars.css'

const sourceRouter = createRouter({
  history: createWebHashHistory(),
  routes: sourceRoutes,
})

createApp(App).use(store).use(sourceRouter).mount('#app')
