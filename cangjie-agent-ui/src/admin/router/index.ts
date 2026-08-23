import { createRouter, createWebHashHistory, Router, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  { path: '/', redirect: '/dashboard' },
  {
    path: '/workflow/design/:id',
    component: () => import('../views/WorkflowDesigner.vue'),
    meta: { title: '工作流编排' }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'model', component: () => import('../views/ModelView.vue'), meta: { title: '模型管理' } },
      { path: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识库' } },
      { path: 'knowledge/:id', component: () => import('../views/KnowledgeDetailView.vue'), meta: { title: '知识库详情' } },
      { path: 'tool', component: () => import('../views/ToolView.vue'), meta: { title: '工具/插件' } },
      { path: 'prompt', component: () => import('../views/PromptView.vue'), meta: { title: '提示词/Skill' } },
      { path: 'workflow', component: () => import('../views/WorkflowView.vue'), meta: { title: '工作流' } },
      { path: 'application', component: () => import('../views/ApplicationView.vue'), meta: { title: '智能应用' } },
      { path: 'channel', component: () => import('../views/ChannelView.vue'), meta: { title: '渠道接入' } },
      { path: 'observability', component: () => import('../views/ObservabilityView.vue'), meta: { title: '可观测性' } },
      { path: 'observability/eval', component: () => import('../views/EvalView.vue'), meta: { title: '评估体系' } },
      { path: 'file', component: () => import('../views/FileView.vue'), meta: { title: '文件管理' } },
      { path: 'system/role', component: () => import('../views/system/RoleView.vue'), meta: { title: '角色管理' } },
      { path: 'system/menu', component: () => import('../views/system/MenuView.vue'), meta: { title: '菜单管理' } },
      { path: 'system', component: () => import('../views/SystemView.vue'), meta: { title: '系统设置' } }
    ]
  }
]

const router: Router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + 'CangJie Agent'
  if (to.meta?.public) { next(); return }
  if (!userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (!userStore.userInfo) {
    try { await userStore.loadUserInfo() } catch { next({ path: '/login' }); return }
  }
  next()
})

export default router
