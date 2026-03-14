<template>
  <div :class="['layout-root', { 'is-mobile': isMobile, 'mobile-open': mobileMenuOpen }]">
    <aside :class="['sidebar', { 'is-collapsed': collapsed }]">
      <div class="sidebar-logo">
        <span v-show="!collapsed" class="logo-text">胸部X光智能辅助诊断系统</span>
      </div>

      <el-scrollbar class="sidebar-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          @select="handleMenuSelect"
          router
          class="sidebar-menu"
          background-color="transparent"
          text-color="rgba(237,246,250,0.7)"
          active-text-color="#edf6fa"
        >
          <template v-if="userStore.isDoctor">
            <el-menu-item index="/cases">
              <el-icon><Folder /></el-icon>
              <template #title>诊断工作</template>
            </el-menu-item>

            <el-menu-item index="/typical-cases">
              <el-icon><Star /></el-icon>
              <template #title>典型病例</template>
            </el-menu-item>
          </template>

          <template v-if="!userStore.isAdmin">
            <el-menu-item index="/statistics">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>质控统计</template>
            </el-menu-item>
          </template>

          <template v-if="userStore.isAdmin">
            <div v-show="!collapsed" class="menu-group-title">系统管理</div>
            <div v-show="collapsed" class="menu-group-divider"></div>

            <el-menu-item index="/users">
              <el-icon><Avatar /></el-icon>
              <template #title>用户管理</template>
            </el-menu-item>

            <el-menu-item index="/config">
              <el-icon><Setting /></el-icon>
              <template #title>系统配置</template>
            </el-menu-item>

            <el-menu-item index="/logs">
              <el-icon><Document /></el-icon>
              <template #title>操作日志</template>
            </el-menu-item>
          </template>

        </el-menu>
      </el-scrollbar>

      <div v-show="!isMobile" class="collapse-btn" @click="toggleCollapse">
        <el-icon :size="16">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <span v-show="!collapsed" class="collapse-text">收起侧栏</span>
      </div>
    </aside>
    <div v-if="isMobile && mobileMenuOpen" class="mobile-backdrop" @click="mobileMenuOpen = false"></div>

    <div class="main-wrapper">
      <header class="top-header">
        <div class="header-left">
          <button v-if="isMobile" class="mobile-menu-btn" @click="mobileMenuOpen = true">
            <el-icon><Menu /></el-icon>
          </button>
          <span class="page-title">{{ currentPageName }}</span>
        </div>

        <div class="header-right">

          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-trigger">
              <el-avatar :size="32" class="user-avatar">
                {{ userStore.userInfo.realName?.charAt(0) || 'U' }}
              </el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ userStore.userInfo.realName || userStore.userInfo.username }}</span>
                <span class="user-role">{{ roleLabel }}</span>
              </div>
              <el-icon :size="12" class="user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="layout-body">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMe } from '@/api/auth'
import { isPreviewMode } from '@/utils/preview'
import {
  Folder,
  Star,
  DataAnalysis,
  Avatar,
  Setting,
  Document,
  Menu,
  SwitchButton,
  Expand,
  Fold,
  ArrowDown
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const publicAccess = import.meta.env.VITE_PUBLIC_ACCESS === 'true'
const isMobile = ref(false)
const mobileMenuOpen = ref(false)
const PAGE_NAME_MAP = {
  '/cases': '诊断工作',
  '/typical-cases': '典型病例',
  '/statistics': '质控统计',
  '/users': '用户管理',
  '/config': '系统配置',
  '/logs': '操作日志'
}

const currentPageName = computed(() => PAGE_NAME_MAP[route.path] || '')

const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', DOCTOR: '医生' }
  return map[userStore.userInfo.roleCode] || userStore.userInfo.roleCode
})

const updateIsMobile = () => {
  const next = window.innerWidth <= 1024
  isMobile.value = next
  if (next) {
    collapsed.value = false
  } else {
    mobileMenuOpen.value = false
  }
}

const toggleCollapse = () => {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value
    return
  }
  collapsed.value = !collapsed.value
}

const handleMenuSelect = () => {
  if (isMobile.value) {
    mobileMenuOpen.value = false
  }
}
const handleCommand = async (command) => {
  if (command === 'logout') {
    await userStore.logout()
    router.push('/login')
  }
}

const refreshUserInfo = async () => {
  if (isPreviewMode() || publicAccess) return
  try {
    const res = await getMe()
    if (res.data) userStore.setUserInfo(res.data)
  } catch (error) {
    console.warn('Failed to refresh user info', error)
  }
}

onMounted(() => {
  refreshUserInfo()
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
.layout-root {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: transparent;
  position: relative;
  z-index: 1;
}

.sidebar {
  width: var(--xrag-sidebar-width);
  min-width: var(--xrag-sidebar-width);
  display: flex;
  flex-direction: column;
  transition: width .25s ease, min-width .25s ease, transform .25s ease;
  position: relative;
  z-index: 100;
  overflow: hidden;
  background: var(--xrag-panel-solid);
  border-right: 1px solid var(--xrag-border);
  box-shadow: var(--xrag-shadow);
  backdrop-filter: blur(18px);
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--xrag-gradient-soft);
  opacity: 0.7;
  pointer-events: none;
}

.sidebar.is-collapsed {
  width: var(--xrag-sidebar-collapsed);
  min-width: var(--xrag-sidebar-collapsed);
}

.sidebar.is-collapsed .sidebar-logo {
  justify-content: center;
  padding: 0;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  height: 72px;
  border-bottom: 1px solid var(--xrag-border);
  flex-shrink: 0;
  overflow: hidden;
  white-space: nowrap;
  background: var(--xrag-panel-2);
}

.logo-text {
  font-size: 14px;
  font-weight: 700;
  color: var(--xrag-text);
  letter-spacing: .12em;
  white-space: nowrap;
}

.sidebar-scroll {
  flex: 1;
  overflow: hidden;
  padding-top: 8px;
}

.sidebar-menu {
  border-right: none !important;
  width: 100% !important;
  background: transparent !important;
}

.sidebar-menu.el-menu--collapse {
  width: var(--xrag-sidebar-collapsed) !important;
}

.menu-group-title {
  padding: 18px 22px 8px;
  font-size: 11px;
  letter-spacing: .14em;
  text-transform: uppercase;
  color: var(--xrag-text-faint);
}

.menu-group-divider {
  height: 1px;
  margin: 12px 16px;
  background: rgba(237, 246, 250, 0.16);
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 44px !important;
  margin: 4px 10px !important;
  padding-left: 14px !important;
  border-radius: 10px !important;
  background: transparent !important;
  border: 1px solid transparent !important;
  color: var(--xrag-text-soft) !important;
  transition: all .2s ease;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(28, 168, 200, 0.12) !important;
  border-color: rgba(28, 168, 200, 0.24) !important;
  color: #f7fbff !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(28, 168, 200, 0.16) !important;
  color: #f7fbff !important;
  border-color: rgba(28, 168, 200, 0.24) !important;
  box-shadow: inset 3px 0 0 var(--xrag-primary) !important;
}

:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  color: inherit !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item),
:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title) {
  width: 48px !important;
  height: 48px !important;
  margin: 8px auto !important;
  padding: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item .el-icon),
:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0 !important;
}

.collapse-btn {
  margin: 12px;
  height: 44px;
  border: 1px solid var(--xrag-border);
  border-radius: var(--xrag-radius-pill);
  background: rgba(10, 26, 38, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--xrag-text-soft);
  cursor: pointer;
  transition: all .2s ease;
}

.sidebar.is-collapsed .collapse-btn {
  margin: 12px 8px;
  justify-content: center;
}

.collapse-btn:hover {
  background: rgba(28, 168, 200, 0.14);
  color: #f7fbff;
}

.collapse-text {
  font-size: 13px;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top-header {
  height: var(--xrag-header-height);
  margin: 16px 16px 0;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--xrag-border);
  border-radius: 18px;
  background: var(--xrag-panel-solid);
  box-shadow: var(--xrag-shadow);
  backdrop-filter: blur(16px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--xrag-text);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 6px 10px 6px 6px;
  border-radius: var(--xrag-radius-pill);
  transition: all .2s ease;
}

.user-trigger:hover {
  background: rgba(28, 168, 200, 0.14);
}

.user-avatar {
  background: rgba(28, 168, 200, 0.2);
  color: #eaf7ff;
}

.user-meta {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
}

.user-caret {
  opacity: 0.5;
  margin-left: 4px;
}

.user-name {
  font-size: 14px;
  color: var(--xrag-text);
}

.user-role {
  font-size: 12px;
  color: var(--xrag-text-soft);
}

.layout-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.mobile-menu-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--xrag-radius-pill);
  border: 1px solid var(--xrag-border);
  background: rgba(10, 26, 38, 0.72);
  color: var(--xrag-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all .2s ease;
}

.mobile-menu-btn:hover {
  background: rgba(28, 168, 200, 0.14);
  border-color: rgba(28, 168, 200, 0.24);
}

.mobile-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 90;
}

.layout-root.is-mobile .sidebar {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: 240px;
  min-width: 240px;
  transform: translateX(-100%);
  z-index: 100;
}

.layout-root.mobile-open .sidebar {
  transform: translateX(0);
}

.layout-root.is-mobile .sidebar.is-collapsed {
  width: 240px;
  min-width: 240px;
}

:deep(.sidebar-scroll .el-scrollbar__wrap) {
  overflow-x: hidden !important;
  overflow-y: auto !important;
  margin-bottom: 0 !important;
}

:deep(.sidebar-scroll .el-scrollbar__bar.is-vertical),
:deep(.sidebar-scroll .el-scrollbar__bar.is-horizontal) {
  display: none !important;
  opacity: 0 !important;
}

@media (max-width: 1024px) {
  .top-header {
    margin: 8px;
    height: 56px;
    padding: 0 12px;
  }

  .page-title {
    font-size: 16px;
  }

  .user-meta {
    display: none;
  }
}

@media (max-width: 768px) {
  .top-header {
    margin: 8px;
    height: 54px;
  }
}
</style>
