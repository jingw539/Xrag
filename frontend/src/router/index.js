import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { loadPreviewToken } from '@/utils/preview'

const resolveHomePath = (userStore) => {
  if (userStore.isAdmin) return '/users'
  return '/cases'
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/Layout.vue'),
    redirect: () => resolveHomePath(useUserStore()),
    meta: { requiresAuth: true },
    children: [
      { path: 'cases', name: 'Workstation', component: () => import('@/views/Workstation.vue'), meta: { requiresDoctor: true } },
      { path: 'cases/:id', name: 'CaseDetail', component: () => import('@/views/CaseDetail.vue'), meta: { requiresDoctor: true } },
      { path: 'typical-cases', name: 'TypicalCaseList', component: () => import('@/views/TypicalCaseList.vue'), meta: { requiresDoctor: true } },
      { path: 'statistics', name: 'Statistics', component: () => import('@/views/Statistics.vue') },
      { path: 'users', name: 'UserManagement', component: () => import('@/views/UserManagement.vue'), meta: { requiresAdmin: true } },
      { path: 'config', name: 'SystemConfig', component: () => import('@/views/SystemConfig.vue'), meta: { requiresAdmin: true } },
      { path: 'logs', name: 'OperationLog', component: () => import('@/views/OperationLog.vue'), meta: { requiresAdmin: true } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const previewToken = loadPreviewToken()
  if (previewToken) {
    if (!userStore.userInfo?.roleCode) {
      userStore.setUserInfo({
        username: 'preview',
        realName: 'Preview User',
        roleCode: 'ADMIN'
      })
    }
    if (to.path === '/login') {
      next(resolveHomePath(userStore))
    } else {
      next()
    }
    return
  }
  if (to.meta.requiresAuth !== false && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next(resolveHomePath(userStore))
  } else if (to.meta.requiresAdmin && !userStore.isAdmin) {
    next(resolveHomePath(userStore))
  } else if (to.meta.requiresDoctor && !userStore.isDoctor) {
    next(resolveHomePath(userStore))
  } else {
    next()
  }
})

export default router
