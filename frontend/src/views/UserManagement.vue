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

      <el-table class="admin-table" :data="users" v-loading="loading" border>
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="roleCode" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="{ ADMIN: 'danger', QC: 'warning', DOCTOR: 'primary' }[row.roleCode]" size="small">
              {{ { ADMIN: '管理员', QC: '质控', DOCTOR: '医生' }[row.roleCode] || row.roleCode }}
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
      <el-form-item label="真实姓名" prop="realName">
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
          placeholder="至少8位，包含字母、数字、特殊字符"
        />
      </el-form-item>
      <el-form-item label="科室">
        <el-input v-model="form.department" placeholder="如：影像科" />
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
          placeholder="至少8位，包含字母、数字、特殊字符(@#$%^&+=!)"
        />
        <div class="pwd-tip">至少8位，需同时包含字母、数字与特殊字符 @#$%^&+=!</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" @click="handleResetPwd">确认重置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser, listUsers, resetPassword, toggleUserStatus, updateUser } from '@/api/user'

const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const total = ref(0)
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
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
}

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
  ElMessage.success('密码已重置')
  pwdVisible.value = false
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除用户“${row.realName || row.username}”吗？`, '删除用户', { type: 'warning' })
    await deleteUser(row.userId)
    ElMessage.success('用户已删除')
    fetchList()
  } catch (_) {
  }
}

const formatDate = (value) => (value ? value.replace('T', ' ').substring(0, 16) : '-')

onMounted(fetchList)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0e1621;
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
  border: 1px solid rgba(111, 134, 166, 0.24);
  background: #1a2535;
  box-shadow: none;
}

:deep(.full-card .el-card__header) {
  border-bottom: 1px solid rgba(111, 134, 166, 0.24);
  background: #1a2535;
}

:deep(.full-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
  background: #1a2535;
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
  color: #9fb3cc;
  margin-top: 4px;
}

:deep(.admin-table) {
  --el-table-bg-color: #121c2a;
  --el-table-tr-bg-color: #121c2a;
  --el-table-row-striped-bg-color: #121c2a;
  --el-table-row-hover-bg-color: #1c2a3d;
  --el-table-header-bg-color: #182538;
  --el-table-border-color: rgba(111, 134, 166, 0.22);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}

:deep(.admin-table .el-table__inner-wrapper),
:deep(.admin-table th.el-table__cell),
:deep(.admin-table tr),
:deep(.admin-table td.el-table__cell) {
  background: #121c2a !important;
}

:deep(.el-pagination),
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
