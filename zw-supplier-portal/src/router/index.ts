import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/Login.vue') },
    { path: '/inquiry', component: () => import('../views/InquiryList.vue'), meta: { requiresAuth: true } },
    { path: '/inquiry/:id', component: () => import('../views/InquiryDetail.vue'), meta: { requiresAuth: true } },
    { path: '/quotation', component: () => import('../views/MyQuotations.vue'), meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to, _from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('supplier_token')) {
    next('/login')
  } else {
    next()
  }
})

export default router
