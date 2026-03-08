# 需求文档

## 简介

本功能旨在为胸部 X 光智能辅助诊断系统提供默认管理员账户初始化能力，确保系统首次部署后能够正常登录和使用。

## 术语表

- **系统 (System)**: 胸部 X 光智能辅助诊断系统
- **管理员账户 (Admin Account)**: 具有系统最高权限的用户账户
- **数据库 (Database)**: 存储系统数据的 PostgreSQL 数据库
- **数据初始化器 (DataInitializer)**: Spring Boot 应用启动时执行的数据初始化组件
- **密码哈希 (Password Hash)**: 使用 BCrypt 算法加密后的密码值
- **BCrypt**: 密码加密算法，由 Spring Security 的 PasswordEncoder 提供

## 需求

### 需求 1：默认管理员账户创建

**用户故事：** 作为系统管理员，我希望系统首次启动时自动创建默认管理员账户，以便我能够登录系统进行初始配置。

#### 验收标准

1. WHEN 应用启动时，THE 系统 SHALL 检查 sys_user 表是否为空
2. IF sys_user 表为空，THEN THE 系统 SHALL 创建一个用户名为 "admin" 的管理员账户
3. WHEN 创建默认管理员账户时，THE 系统 SHALL 使用 PasswordEncoder 将密码 "123456" 进行 BCrypt 哈希加密
4. WHEN 创建默认管理员账户时，THE 系统 SHALL 将账户状态设置为启用状态（status=1）
5. WHEN 创建默认管理员账户时，THE 系统 SHALL 将账户关联到管理员角色（ADMIN 角色）
6. WHEN 成功创建默认管理员账户后，THE 系统 SHALL 在启动日志中打印 "默认管理员账号已创建 — 用户名: admin  密码: 123456"

### 需求 2：角色和权限初始化

**用户故事：** 作为系统管理员，我希望系统预置基本的角色数据，以便进行用户权限管理。

#### 验收标准

1. WHEN 应用启动时，THE 系统 SHALL 检查 sys_role 表是否为空
2. IF sys_role 表为空，THEN THE 系统 SHALL 创建 "ADMIN" 角色，角色代码为 "ADMIN"，角色名称为 "系统管理员"
3. IF sys_role 表为空，THEN THE 系统 SHALL 创建 "DOCTOR" 角色，角色代码为 "DOCTOR"，角色名称为 "医生"
4. IF sys_role 表为空，THEN THE 系统 SHALL 创建 "QC" 角色，角色代码为 "QC"，角色名称为 "质控员"
5. WHEN 创建角色时，THE 系统 SHALL 为每个角色分配唯一的角色ID

### 需求 3：数据完整性保证

**用户故事：** 作为开发人员，我希望初始化逻辑具有幂等性，以便多次启动应用不会导致数据重复或错误。

#### 验收标准

1. WHEN 应用启动时，THE 系统 SHALL 检查 sys_role 表是否包含数据
2. IF sys_role 表已包含数据，THEN THE 系统 SHALL 跳过角色创建步骤
3. WHEN 应用启动时，THE 系统 SHALL 检查 sys_user 表是否包含数据
4. IF sys_user 表已包含数据，THEN THE 系统 SHALL 跳过用户创建步骤
5. WHEN 数据初始化完成后，THE 系统 SHALL 在启动日志中记录初始化状态

### 需求 4：安全性要求

**用户故事：** 作为安全管理员，我希望系统提示用户修改默认密码，以便提高系统安全性。

#### 验收标准

1. THE 系统 SHALL 在启动日志中明确显示默认管理员的用户名和密码
2. THE 系统 SHALL 在启动日志中包含 "首次登录后请立即修改密码" 的提示信息
3. THE 系统 SHALL 在文档中说明密码策略要求：密码长度大于等于8位，且包含字母、数字和特殊字符
4. WHEN 用户修改密码时，THE 系统 SHALL 验证新密码符合密码策略（≥8位，含字母+数字+特殊字符）
