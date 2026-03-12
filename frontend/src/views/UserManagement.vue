<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <div class="card-header">
          <span class="page-title"><el-icon><Avatar /></el-icon> 用户管理</span>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建用户
          </el-button>
        </div>
      </template>

      <el-table v-if="!isMobile" class="admin-table" :data="users" v-loading="loading" border>
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="roleCode" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.roleCode)" size="small">
              {{ roleLabel(row.roleCode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="科室" min-width="100" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="160">
          <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-divider direction="vertical" />
            <el-button size="small" type="warning" link @click="openResetPwd(row)">重置密码</el-button>
            <el-divider direction="vertical" />
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="user-card-list" v-loading="loading">
        <div v-for="row in users" :key="row.userId" class="user-card">
          <div class="user-card-top">
            <div class="user-identity">
              <span class="user-name">{{ row.realName || row.username }}</span>
              <span class="user-username">@{{ row.username }}</span>
            </div>
            <el-tag :type="roleTagType(row.roleCode)" size="small">
              {{ roleLabel(row.roleCode) }}
            </el-tag>
          </div>
          <div class="user-card-meta">
            <span>{{ row.department || '—' }}</span>
            <span>{{ formatDate(row.lastLoginAt) }}</span>
          </div>
          <div class="user-card-status">
            <span>{{ row.status === 1 ? '启用' : '禁用' }}</span>
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </div>
          <div class="user-card-actions">
            <el-button size="small" type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click.stop="openResetPwd(row)">重置</el-button>
            <el-button size="small" type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="users.length === 0" description="暂无用户" :image-size="60" />
      </div>

      <el-pagination
        class="pagination"
        :current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="value => { query.page = value; fetchList() }"
      />
    </el-card>
  </div>

  <el-dialog v-model="formVisible" :title="editingId ? '编辑用户' : '新建用户'" width="460px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="!!editingId" />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" />
      </el-form-item>
      <el-form-item v-if="!editingId" label="角色" prop="roleCode">
        <el-select v-model="form.roleCode" style="width: 100%">
          <el-option label="医生" value="DOCTOR" />
          <el-option label="质控" value="QC" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!editingId" label="初始密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="至少 8 位，包含字母、数字和特殊字符"
        />
      </el-form-item>
      <el-form-item label="科室">
        <el-input v-model="form.department" placeholder="例如：影像科" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="pwdVisible" title="重置密码" width="380px">
    <el-form label-width="80px">
      <el-form-item label="新密码">
        <el-input
          v-model="newPwd"
          type="password"
          show-password
          placeholder="至少 8 位，包含字母、数字和特殊字符，如 @#$%^&+=!"
        />
        <div class="pwd-tip">至少 8 位，需同时包含字母、数字与特殊字符 @#$%^&+=!</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" @click="handleResetPwd">确认重置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Avatar, Plus } from '@element-plus/icons-vue'
import { createUser, deleteUser, listUsers, resetPassword, toggleUserStatus, updateUser } from '@/api/user'

const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const total = ref(0)
const isMobile = ref(false)
const formVisible = ref(false)
const pwdVisible = ref(false)
const editingId = ref(null)
const pwdTargetId = ref(null)
const newPwd = ref('')
const formRef = ref(null)

const query = reactive({ page: 1, pageSize: 20 })
const form = reactive({ username: '', realName: '', roleCode: 'DOCTOR', password: '', department: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
}

const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

const roleLabel = (code) => ({ ADMIN: '管理员', QC: '质控', DOCTOR: '医生' }[code] || code || '-')
const roleTagType = (code) => ({ ADMIN: 'danger', QC: 'warning', DOCTOR: 'primary' }[code] || 'info')

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listUsers(query)
    users.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  Object.assign(form, { username: '', realName: '', roleCode: 'DOCTOR', password: '', department: '' })
  formVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.userId
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    roleCode: row.roleCode,
    password: '',
    department: row.department || ''
  })
  formVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, { realName: form.realName, department: form.department })
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName,
        roleCode: form.roleCode,
        department: form.department
      })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  await toggleUserStatus(row.userId, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

const openResetPwd = (row) => {
  pwdTargetId.value = row.userId
  newPwd.value = ''
  pwdVisible.value = true
}

const handleResetPwd = async () => {
  if (!newPwd.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  await resetPassword(pwdTargetId.value, newPwd.value)
  ElMessage.success('密码重置成功')
  pwdVisible.value = false
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除用户“${row.realName || row.username}”吗？`, '删除用户', { type: 'warning' })
    await deleteUser(row.userId)
    ElMessage.success('用户删除成功')
    fetchList()
  } catch { /* ignore */ }
}

const formatDate = (value) => (value ? value.replace('T', ' ').substring(0, 16) : '-')

onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  fetchList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0d1420;
  color: #d0dcf0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.full-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
  box-shadow: none;
}

:deep(.full-card .el-card__header) {
  border-bottom: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
}

:deep(.full-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
  background: #0d1420;
}

:deep(.full-card .el-table) {
  flex: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e8f0ff;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.pwd-tip {
  font-size: 11px;
  color: rgba(220, 231, 247, 0.7);
  margin-top: 8px;
  line-height: 1.4;
}

:deep(.admin-table) {
  --el-table-bg-color: #0f1923;
  --el-table-tr-bg-color: #0f1923;
  --el-table-row-striped-bg-color: #0f1923;
  --el-table-row-hover-bg-color: rgba(111, 134, 166, 0.12);
  --el-table-header-bg-color: rgba(13, 20, 32, 0.95);
  --el-table-border-color: rgba(111, 134, 166, 0.16);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}

:deep(.admin-table .el-table__inner-wrapper),
:deep(.admin-table th.el-table__cell),
:deep(.admin-table tr),
:deep(.admin-table td.el-table__cell) {
  background: #0f1923 !important;
}

/* 分页：按钮、页码去白底 */
:deep(.el-pagination) {
  color: #d0dcf0;
}
:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  background: #0f1923 !important;
  color: #d0dcf0 !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
}
:deep(.el-pagination .el-pager li.is-active) {
  background: rgba(64, 158, 255, 0.35) !important;
  color: #fff !important;
  border-color: rgba(64, 158, 255, 0.5) !important;
}
:deep(.el-pagination .btn-prev:not(:disabled):hover),
:deep(.el-pagination .btn-next:not(:disabled):hover),
:deep(.el-pagination .el-pager li:not(.is-active):hover) {
  background: rgba(111, 134, 166, 0.2) !important;
  color: #e8f0ff !important;
}

/* 角色标签去白底 */
:deep(.admin-table .el-tag) {
  background: rgba(111, 134, 166, 0.2) !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
  color: #d0dcf0 !important;
}
:deep(.admin-table .el-tag.el-tag--primary) {
  background: rgba(64, 158, 255, 0.25) !important;
  border-color: rgba(64, 158, 255, 0.4) !important;
  color: #7eb8ff !important;
}
:deep(.admin-table .el-tag.el-tag--danger) {
  background: rgba(245, 108, 108, 0.2) !important;
  border-color: rgba(245, 108, 108, 0.4) !important;
  color: #f89898 !important;
}
:deep(.admin-table .el-tag.el-tag--warning) {
  background: rgba(230, 162, 60, 0.2) !important;
  border-color: rgba(230, 162, 60, 0.4) !important;
  color: #e6c078 !important;
}

:deep(.el-form-item__label),
:deep(.el-dialog__title),
:deep(.el-descriptions__label),
:deep(.el-descriptions__content) {
  color: #d0dcf0;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper) {
  background: #111a27;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.22) inset;
}
</style>

<style scoped>
.page-wrap {
  background: var(--xrag-bg) !important;
  color: var(--xrag-text) !important;
}

.full-card {
  background: var(--xrag-bg) !important;
  border-color: var(--xrag-border) !important;
  box-shadow: var(--xrag-shadow) !important;
}

:deep(.full-card .el-card__header),
:deep(.full-card .el-card__body) {
  background: var(--xrag-bg) !important;
  border-color: var(--xrag-border) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-form-item__label),
:deep(.el-dialog__title),
:deep(.el-empty__description),
:deep(.el-pagination__total),
:deep(.el-pagination__jump),
:deep(.el-table .cell),
:deep(.el-descriptions__label),
:deep(.el-descriptions__content) {
  color: var(--xrag-text) !important;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  background: rgba(233, 238, 245, 0.05) !important;
  border-color: var(--xrag-border-strong) !important;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.18) inset !important;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner),
:deep(.el-select__placeholder),
:deep(.el-select__selected-item),
:deep(.el-range-input),
:deep(.el-switch__label) {
  color: var(--xrag-text) !important;
}

:deep(.el-input__inner::placeholder) {
  color: rgba(220, 231, 247, 0.55) !important;
}

:deep(.el-button--default),
:deep(.el-button.is-link),
:deep(.el-button.is-plain) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-button--default:hover),
:deep(.el-button.is-link:hover),
:deep(.el-button.is-plain:hover) {
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.28) !important;
  color: #f4f8ff !important;
}

:deep(.el-button--primary) {
  background: linear-gradient(180deg, #4A9EFF 0%, #3A86E8 100%) !important;
  border-color: #4A9EFF !important;
  color: #fff !important;
}

:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell),
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__empty-block),
:deep(.el-descriptions__body),
:deep(.el-descriptions__table) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}

:deep(.el-table--border::before),
:deep(.el-table--border::after),
:deep(.el-table__inner-wrapper::before),
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell),
:deep(.el-descriptions__cell) {
  border-color: rgba(111, 134, 166, 0.24) !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(111, 134, 166, 0.12) !important;
}

:deep(.el-tag),
:deep(.el-badge__content) {
  background: rgba(111, 134, 166, 0.2) !important;
  color: var(--xrag-text) !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
}

:deep(.el-dialog),
:deep(.el-dialog__header),
:deep(.el-dialog__body),
:deep(.el-dialog__footer) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}

:deep(.el-dialog) {
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.35) !important;
  border: 1px solid rgba(111, 134, 166, 0.25) !important;
}

:deep(.el-dialog__title) {
  color: #eaf2ff !important;
  font-weight: 600;
}

:deep(.el-dialog__footer .el-button--default) {
  background: rgba(233, 238, 245, 0.08) !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
  color: #dbe7f7 !important;
}

:deep(.el-dialog__footer .el-button--default:hover) {
  background: rgba(74, 158, 255, 0.14) !important;
  border-color: rgba(74, 158, 255, 0.4) !important;
  color: #f4f8ff !important;
}

:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-pagination .el-pager li.is-active) {
  background: rgba(74, 158, 255, 0.24) !important;
  border-color: rgba(74, 158, 255, 0.42) !important;
  color: #ffffff !important;
}

.user-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--xrag-border);
  background: var(--xrag-panel);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.user-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 600;
  color: var(--xrag-text);
}

.user-username {
  font-size: 12px;
  color: var(--xrag-text-faint);
}

.user-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--xrag-text-soft);
}

.user-card-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--xrag-text-soft);
}

.user-card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .page-wrap {
    padding: 12px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .pagination {
    justify-content: center;
  }
}
</style>
