<template>
  <div class="layout-root">
    <aside :class="['sidebar', { 'is-collapsed': collapsed }]">
      <div class="sidebar-logo">
        <span v-show="!collapsed" class="logo-text">胸部X光智能辅助诊断系统</span>
      </div>

      <el-scrollbar class="sidebar-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          router
          class="sidebar-menu"
          background-color="#0d1420"
          text-color="rgba(255,255,255,0.68)"
          active-text-color="#ffffff"
        >
          <template v-if="userStore.isDoctor">
            <el-menu-item index="/cases">
              <el-icon><Folder /></el-icon>
              <template #title>诊断工作台</template>
            </el-menu-item>

            <el-menu-item index="/typical-cases">
              <el-icon><Star /></el-icon>
              <template #title>典型病例库</template>
            </el-menu-item>
          </template>

          <template v-if="!userStore.isAdmin">
            <el-menu-item index="/alerts">
              <el-icon><Bell /></el-icon>
              <template #title>
                <span>危急值预警</span>
                <el-tag v-if="pendingAlertCount" type="danger" size="small" round class="menu-badge">
                  {{ pendingAlertCount }}
                </el-tag>
              </template>
            </el-menu-item>

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

      <div class="collapse-btn" @click="collapsed = !collapsed">
        <el-icon :size="16">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <span v-show="!collapsed" class="collapse-text">收起侧栏</span>
      </div>
    </aside>

    <div class="main-wrapper">
      <header class="top-header">
        <div class="header-left">
          <span class="page-title">{{ currentPageName }}</span>
        </div>

        <div class="header-right">
          <el-tooltip v-if="!userStore.isAdmin" content="危急值预警" placement="bottom">
            <div class="header-action-btn" @click="router.push('/alerts')">
              <el-badge :value="pendingAlertCount" :hidden="!pendingAlertCount" type="danger">
                <el-icon :size="18"><Bell /></el-icon>
              </el-badge>
            </div>
          </el-tooltip>

          <el-divider v-if="!userStore.isAdmin" direction="vertical" style="height:20px;margin:0 12px;" />

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
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPendingCount } from '@/api/alert'
import { getMe } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const pendingAlertCount = ref(0)

const PAGE_NAME_MAP = {
  '/cases': '诊断工作台',
  '/typical-cases': '典型病例库',
  '/alerts': '危急值预警',
  '/statistics': '质控统计',
  '/users': '用户管理',
  '/config': '系统配置',
  '/logs': '操作日志'
}

const currentPageName = computed(() => PAGE_NAME_MAP[route.path] || '')

const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', QC: '质控员', DOCTOR: '医生' }
  return map[userStore.userInfo.roleCode] || userStore.userInfo.roleCode
})

const fetchPendingCount = async () => {
  if (userStore.isAdmin) {
    pendingAlertCount.value = 0
    return
  }
  try {
    const res = await getPendingCount()
    pendingAlertCount.value = res.data || 0
  } catch (_) {}
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
  } catch (_) {}
}

onMounted(() => {
  refreshUserInfo()
  fetchPendingCount()
  setInterval(fetchPendingCount, 60000)
})
</script>
<style scoped>
.layout-root {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.08), transparent 20%),
    linear-gradient(180deg, #0d1420 0%, #0a1018 100%);
}

.sidebar {
  width: 220px;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  transition: width .25s ease, min-width .25s ease;
  position: relative;
  z-index: 100;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(15, 25, 40, 0.98) 0%, rgba(10, 16, 24, 0.98) 100%);
  border-right: 1px solid rgba(111, 134, 166, 0.2);
  box-shadow:
    0 18px 40px rgba(0, 0, 0, 0.3),
    inset -1px 0 0 rgba(111, 134, 166, 0.1);
  backdrop-filter: blur(24px) saturate(150%);
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
  width: 64px;
  min-width: 64px;
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
  border-bottom: 1px solid rgba(111, 134, 166, 0.2);
  flex-shrink: 0;
  overflow: hidden;
  white-space: nowrap;
  background: linear-gradient(180deg, rgba(111, 134, 166, 0.06) 0%, transparent 100%);
}

:deep(.sidebar-logo .el-icon) {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.logo-text {
  font-size: 14px;
  font-weight: 700;
  color: #e8f0ff;
  letter-spacing: .8px;
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
  width: 64px !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item) {
  margin: 7px 8px;
  padding: 0 !important;
  justify-content: center;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0 !important;
  margin-left: 0 !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title) {
  padding: 0 !important;
  justify-content: center;
}

:deep(.sidebar-menu.el-menu) {
  background: transparent !important;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  background-clip: padding-box;
}

:deep(.sidebar-menu .el-menu-item span),
:deep(.sidebar-menu .el-menu-item div),
:deep(.sidebar-menu .el-sub-menu__title span),
:deep(.sidebar-menu .el-sub-menu__title div) {
  background: transparent !important;
}

.menu-group-title {
  padding: 18px 22px 8px;
  font-size: 11px;
  letter-spacing: .14em;
  text-transform: uppercase;
  color: rgba(103, 122, 144, 0.7);
}

.menu-group-divider {
  height: 1px;
  margin: 12px 16px;
  background: rgba(111, 134, 166, 0.2);
}

.menu-badge {
  margin-left: 8px;
  border: none;
  box-shadow: 0 6px 14px rgba(245, 108, 108, 0.24);
}

:deep(.sidebar-menu .el-menu-item) {
  height: 48px;
  margin: 7px 12px;
  border-radius: 14px;
  border: 1px solid transparent;
  color: rgba(208, 220, 240, 0.8) !important;
  background: rgba(111, 134, 166, 0.12) !important;
  transition: all .2s ease;
}

:deep(.sidebar-menu .el-menu-item:hover) {
  background: rgba(111, 134, 166, 0.2) !important;
  border-color: rgba(111, 134, 166, 0.25);
  transform: translateX(1px);
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  color: #7eb8ff !important;
  background: rgba(64, 158, 255, 0.2) !important;
  border-color: rgba(64, 158, 255, 0.35);
}

:deep(.sidebar-menu .el-menu-item .el-icon) {
  font-size: 16px;
  color: inherit !important;
}

:deep(.sidebar-menu .el-menu-item .el-menu-tooltip__trigger) {
  color: inherit !important;
}

.collapse-btn {
  margin: 12px;
  height: 44px;
  border: 1px solid rgba(111, 134, 166, 0.25);
  border-radius: 14px;
  background: rgba(111, 134, 166, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(208, 220, 240, 0.8);
  cursor: pointer;
  transition: all .2s ease;
}

.sidebar.is-collapsed .collapse-btn {
  margin: 12px 8px;
  justify-content: center;
}

.collapse-btn:hover {
  background: rgba(111, 134, 166, 0.2);
  color: #d0dcf0;
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
  height: 64px;
  margin: 16px 16px 0;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid rgba(111, 134, 166, 0.2);
  border-radius: 16px;
  background: rgba(15, 25, 35, 0.9);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(8px);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #e8f0ff;
}

.header-right {
  display: flex;
  align-items: center;
}

.header-action-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #d0dcf0;
  background: rgba(111, 134, 166, 0.12);
  transition: all .2s ease;
}

.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(74, 158, 255, 0.12);
}

.user-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 6px 10px 6px 6px;
  border-radius: 14px;
  transition: all .2s ease;
}

.user-avatar {
  background: #409eff;
  color: #fff;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.24);
}

.user-meta {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.user-role {
  font-size: 12px;
  color: #909399;
}

.layout-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
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

:deep(.sidebar-scroll .el-scrollbar__view) {
  min-height: 100%;
  overflow: hidden !important;
}

:deep(.sidebar-menu.el-menu--collapse) {
  width: 64px !important;
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

:deep(.sidebar-menu.el-menu--collapse .el-menu-tooltip__trigger) {
  width: 100% !important;
  height: 100% !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__icon-arrow) {
  display: none !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item .el-icon),
:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0 !important;
  width: 16px !important;
  height: 16px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  background: rgba(255,255,255,0.34) !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(255,255,255,0.82) !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: linear-gradient(180deg, rgba(255,255,255,0.98), rgba(241,247,255,0.95)) !important;
}

.layout-root {
  background: linear-gradient(180deg, #d8e0e8 0%, #d1d9e1 100%);
}

.sidebar {
  background: linear-gradient(180deg, rgba(228, 234, 240, 0.94), rgba(220, 227, 234, 0.94)) !important;
  border-right: 1px solid rgba(179, 191, 202, 0.52) !important;
  box-shadow: 8px 0 24px rgba(71, 82, 95, 0.06) !important;
}

.sidebar-logo {
  background: linear-gradient(180deg, rgba(229, 235, 241, 0.86) 0%, rgba(223, 229, 236, 0.76) 100%) !important;
  border-bottom-color: rgba(182, 194, 205, 0.58) !important;
}

.logo-text {
  color: #3b4b5c;
  text-shadow: none;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  color: #4c5d70 !important;
  background: rgba(236, 241, 246, 0.62) !important;
  border-color: transparent !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  color: #405161 !important;
  background: rgba(227, 234, 241, 0.9) !important;
  border-color: rgba(186, 198, 210, 0.7) !important;
  transform: none !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  color: #33485d !important;
  background: linear-gradient(180deg, rgba(223, 231, 239, 0.98), rgba(214, 223, 232, 0.98)) !important;
  border-color: rgba(176, 189, 203, 0.78) !important;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.22) !important;
}

.collapse-btn,
.header-action-btn,
.user-trigger,
.top-header {
  box-shadow: none !important;
}

.collapse-btn {
  background: linear-gradient(180deg, rgba(232, 237, 242, 0.9), rgba(223, 229, 235, 0.9)) !important;
  border-color: rgba(182, 194, 206, 0.8) !important;
  color: #56677a !important;
}

.collapse-btn:hover,
.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(224, 231, 237, 0.92) !important;
  color: #435466 !important;
}

.top-header {
  background: rgba(229, 235, 240, 0.92) !important;
  border-color: rgba(183, 194, 205, 0.7) !important;
}

.page-title,
.user-name {
  color: #334455 !important;
}

.user-role {
  color: #708091 !important;
}

.user-avatar {
  background: #71839a !important;
  box-shadow: none !important;
}

.layout-body {
  background: linear-gradient(180deg, rgba(214, 222, 230, 0.42), rgba(210, 218, 226, 0.28));
}

.layout-root {
  background: #0E1621 !important;
}

.sidebar {
  background: #2C3E57 !important;
  border-right: 1px solid #6F86A6 !important;
  box-shadow: none !important;
}

.sidebar-logo,
.top-header,
.collapse-btn,
.header-action-btn,
.user-trigger {
  background: #2C3E57 !important;
}

.sidebar-logo {
  border-bottom-color: #6F86A6 !important;
}

.logo-text,
.page-title,
.user-name,
:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title),
:deep(.sidebar-menu .el-menu-item .el-icon) {
  color: #E9EEF5 !important;
}

.user-role,
.menu-group-title,
.collapse-btn {
  color: #B7C5D6 !important;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  background: transparent !important;
  border-color: transparent !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover),
.collapse-btn:hover,
.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(111, 134, 166, 0.16) !important;
  border-color: #6F86A6 !important;
  color: #E9EEF5 !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(111, 134, 166, 0.26) !important;
  border: 1px solid #6F86A6 !important;
  color: #E9EEF5 !important;
  box-shadow: inset 3px 0 0 #E9EEF5 !important;
}

.top-header {
  border: 1px solid #6F86A6 !important;
  box-shadow: none !important;
}

.user-avatar {
  background: #6F86A6 !important;
  box-shadow: none !important;
}

.layout-body {
  background: #0E1621 !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover),
.collapse-btn:hover,
.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(111, 134, 166, 0.16) !important;
  border-color: #6F86A6 !important;
  color: #E9EEF5 !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(111, 134, 166, 0.26) !important;
  border: 1px solid #6F86A6 !important;
  color: #E9EEF5 !important;
  box-shadow: inset 3px 0 0 #E9EEF5 !important;
}

/* ===== Final PACS hierarchy override ===== */
.layout-root,
.layout-body {
  background: #0E1621 !important;
}

.sidebar {
  background: #0F1923 !important;
  border-right: 1px solid rgba(111, 134, 166, 0.42) !important;
}

.sidebar-logo,
.top-header,
.collapse-btn,
.header-action-btn,
.user-trigger {
  background: #0F1923 !important;
}

.sidebar-logo,
.top-header {
  border-color: rgba(111, 134, 166, 0.42) !important;
}

.logo-text,
.page-title,
.user-name,
:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title),
:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  color: #D0DCF0 !important;
}

.user-role,
.menu-group-title,
.collapse-btn {
  color: rgba(208, 220, 240, 0.68) !important;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 44px !important;
  margin: 4px 10px !important;
  padding-left: 14px !important;
  border-radius: 8px !important;
  background: transparent !important;
  border: 1px solid transparent !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover),
.collapse-btn:hover,
.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(74, 158, 255, 0.08) !important;
  border-color: rgba(74, 158, 255, 0.22) !important;
  color: #E9EEF5 !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(74, 158, 255, 0.08) !important;
  border-color: transparent !important;
  color: #E9EEF5 !important;
  box-shadow: inset 3px 0 0 #4A9EFF !important;
}

.user-avatar {
  display: none !important;
}

.user-meta {
  margin-left: 0 !important;
}
</style>




<style scoped>
/* XRAG final layout polish */
.layout-root,
.layout-body {
  background: var(--xrag-bg) !important;
}

.sidebar,
.sidebar-logo,
.top-header,
.collapse-btn,
.header-action-btn,
.user-trigger {
  background: var(--xrag-panel) !important;
}

.sidebar {
  border-right: 1px solid rgba(111, 134, 166, 0.32) !important;
}

.sidebar-logo,
.top-header {
  border-color: rgba(111, 134, 166, 0.28) !important;
}

.logo-text,
.page-title,
.user-name,
:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title),
:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  color: var(--xrag-text) !important;
}

.user-role,
.menu-group-title,
.collapse-btn,
.collapse-text {
  color: var(--xrag-text-soft) !important;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 44px !important;
  margin: 4px 10px !important;
  padding-left: 14px !important;
  border-radius: 10px !important;
  background: transparent !important;
  border: 1px solid transparent !important;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover),
.collapse-btn:hover,
.header-action-btn:hover,
.user-trigger:hover {
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.22) !important;
  color: #f4f8ff !important;
  box-shadow: none !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: rgba(74, 158, 255, 0.12) !important;
  color: #f4f8ff !important;
  border-color: rgba(74, 158, 255, 0.14) !important;
  box-shadow: inset 3px 0 0 #4A9EFF !important;
}

.user-avatar {
  display: none !important;
}

.user-meta {
  margin-left: 0 !important;
}
</style>
