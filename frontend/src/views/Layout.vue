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
          background-color="#0d1420"
          text-color="rgba(255,255,255,0.68)"
          active-text-color="#ffffff"
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

          <template v-if="userStore.isQC">
            <div v-show="!collapsed" class="menu-group-title">质控管理</div>
            <div v-show="collapsed" class="menu-group-divider"></div>

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
              <el-icon :size="12" style="opacity:.5;margin-left:4px;"><ArrowDown /></el-icon>
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
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
  const map = { ADMIN: '管理员', QC: '质控', DOCTOR: '医生' }
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
  background: var(--xrag-bg);
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
  background: var(--xrag-panel);
  border-right: 1px solid var(--xrag-border);
  box-shadow: var(--xrag-shadow);
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.08), transparent 22%),
    linear-gradient(180deg, rgba(111, 134, 166, 0.08) 0%, transparent 18%);
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
  letter-spacing: .08em;
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
  background: rgba(111, 134, 166, 0.2);
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
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.22) !important;
  color: #f4f8ff !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(74, 158, 255, 0.12) !important;
  color: #f4f8ff !important;
  border-color: rgba(74, 158, 255, 0.14) !important;
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
  border-radius: 12px;
  background: rgba(111, 134, 166, 0.1);
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
  background: rgba(74, 158, 255, 0.12);
  color: #f4f8ff;
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
  border-radius: 16px;
  background: var(--xrag-panel);
  box-shadow: var(--xrag-shadow);
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
  border-radius: 12px;
  transition: all .2s ease;
}

.user-trigger:hover {
  background: rgba(74, 158, 255, 0.12);
}

.user-avatar {
  background: rgba(74, 158, 255, 0.22);
  color: #e9f1ff;
}

.user-meta {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
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
  border-radius: 10px;
  border: 1px solid rgba(111, 134, 166, 0.28);
  background: rgba(111, 134, 166, 0.12);
  color: var(--xrag-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all .2s ease;
}

.mobile-menu-btn:hover {
  background: rgba(74, 158, 255, 0.12);
  border-color: rgba(74, 158, 255, 0.22);
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
