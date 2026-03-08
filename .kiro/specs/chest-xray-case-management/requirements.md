# Requirements Document

## 1. Introduction

本需求文档定义胸部 X 光智能辅助诊断系统中的病例与影像管理核心功能，以及相关的性能与审计要求。

系统采用分层单体架构，基于 Spring Boot + Vue 3 + openGauss + MinIO 技术栈构建，面向放射科医生提供病例信息管理、影像浏览、报告生命周期管理等基础能力，为后续 AI 辅助诊断和报告生成提供数据基础。

系统遵循高内聚低耦合、云原生、安全优先的设计原则，支持在 openEuler 操作系统上以容器化方式部署。

## 2. Glossary

- **System**：胸部 X 光智能辅助诊断系统
- **Case**：病例，一次胸部 X 光检查的完整记录，包含患者匿名信息、检查号、检查时间等
- **Image**：影像，与病例关联的胸片图像文件，支持 JPG、PNG、DICOM 格式
- **Report**：报告，针对病例生成的放射学诊断报告，通常包含 Findings（影像所见）和 Impression（影像印象）
- **User**：用户，包括放射科医生、质控人员、系统管理员
- **MinIO**：对象存储服务，用于存储影像文件和 PDF 报告等非结构化数据
- **openGauss**：关系型数据库，用于存储结构化数据
- **DICOM**：医学数字成像和通信标准，用于医学影像存储和传输
- **PACS**：影像归档与通信系统，医院现有影像管理系统
- **HIS**：医院信息系统
- **RIS**：放射信息系统
- **JWT**：JSON Web Token，用于用户身份认证
- **RBAC**：基于角色的访问控制模型

## 3. Functional Requirements

### FR-1 病例列表查看与检索

**User Story:** 作为放射科医生，我希望能够查看和检索病例列表，以便快速找到需要处理的检查记录。

#### Acceptance Criteria

1. WHEN 医生访问病例列表页面，THE System SHALL 显示所有病例记录，包括检查号、患者匿名 ID、性别、年龄、检查时间、检查部位、科室、报告状态
2. WHEN 医生输入检查号或患者匿名 ID 进行搜索，THE System SHALL 返回匹配的病例记录列表
3. WHEN 医生选择检查时间范围进行筛选，THE System SHALL 仅显示该时间范围内的病例
4. WHEN 医生选择报告状态进行筛选，THE System SHALL 仅显示对应状态的病例（未生成、AI 草稿、编辑中、已签发）
5. WHEN 医生点击列表中的某条病例记录，THE System SHALL 跳转到该病例的详情页面

### FR-2 影像上传与管理

**User Story:** 作为放射科医生，我希望能够上传和管理胸部 X 光影像，以便为诊断提供影像资料。

#### Acceptance Criteria

1. WHEN 医生在病例详情页点击"上传影像"按钮，THE System SHALL 打开文件选择对话框
2. WHEN 医生选择 JPG、PNG 或 DICOM 格式的影像文件，THE System SHALL 将文件上传至 MinIO 对象存储
3. WHEN 影像上传成功，THE System SHALL 在 openGauss 数据库中创建影像元数据记录，包括文件路径、投照体位、图像尺寸、拍摄时间
4. WHEN 影像文件大小超过 50MB，THE System SHALL 拒绝上传并提示"文件过大，请压缩或分批上传"
5. WHEN 医生查看病例详情，THE System SHALL 显示该病例关联的所有影像缩略图

### FR-3 影像浏览与基础交互

**User Story:** 作为放射科医生，我希望能够浏览胸部 X 光影像并进行基本操作，以便仔细观察影像细节。

#### Acceptance Criteria

1. WHEN 医生点击影像缩略图，THE System SHALL 在影像查看器中加载并显示完整影像
2. WHEN 医生使用鼠标滚轮操作，THE System SHALL 对影像进行缩放
3. WHEN 医生按住鼠标左键拖动，THE System SHALL 对影像进行平移
4. WHEN 医生点击"窗宽窗位"调节按钮，THE System SHALL 提供滑块控件调整影像显示参数
5. WHEN 医生点击"旋转"按钮，THE System SHALL 将影像顺时针旋转 90 度

### FR-4 病例信息创建与编辑

**User Story:** 作为放射科医生，我希望能够创建和编辑病例基本信息，以便完善检查记录。

#### Acceptance Criteria

1. WHEN 医生点击"新建病例"按钮，THE System SHALL 显示病例信息录入表单
2. WHEN 医生填写必填字段（检查号、患者匿名 ID、检查时间、检查部位）并提交，THE System SHALL 在数据库中创建病例记录
3. WHEN 检查号已存在于系统中，THE System SHALL 拒绝创建并提示"检查号已存在，请检查是否重复录入"
4. WHEN 医生在病例详情页点击"编辑"按钮，THE System SHALL 允许修改患者匿名 ID、性别、年龄、科室等字段
5. WHEN 医生保存修改后的病例信息，THE System SHALL 更新数据库记录并记录更新时间

### FR-5 典型病例标记与管理

**User Story:** 作为放射科医生，我希望能够标记典型病例，以便用于教学和科研。

#### Acceptance Criteria

1. WHEN 医生在病例详情页点击"标记为典型病例"按钮，THE System SHALL 显示标签和备注输入框
2. WHEN 医生输入典型标签（如"经典肺炎"）和备注说明并确认，THE System SHALL 将病例的 is_typical 字段设置为 1 并保存标签和备注
3. WHEN 医生访问典型病例列表页面，THE System SHALL 仅显示 is_typical 为 1 的病例
4. WHEN 医生在典型病例列表中按病理类别筛选，THE System SHALL 根据典型标签进行过滤
5. WHEN 医生点击"取消典型标记"按钮，THE System SHALL 将病例的 is_typical 字段设置为 0，并从典型病例列表中移除

### FR-6 批量导入病例数据

**User Story:** 作为系统管理员，我希望能够批量导入病例数据，以便快速初始化系统或导入历史数据。

#### Acceptance Criteria

1. WHEN 管理员在数据导入页面上传符合模板格式的 CSV 文件，THE System SHALL 解析文件内容
2. WHEN CSV 文件中的病例数据格式正确，THE System SHALL 批量创建病例记录并返回成功导入的数量
3. WHEN CSV 文件中存在格式错误或必填字段缺失，THE System SHALL 拒绝导入并返回错误行号和错误原因
4. WHEN 批量导入过程中遇到检查号重复，THE System SHALL 跳过该条记录并在导入报告中标注
5. WHEN 批量导入完成，THE System SHALL 在操作日志中记录导入操作、导入用户、导入时间和导入数量

### FR-7 病例删除与级联清理

**User Story:** 作为放射科医生，我希望能够删除错误的病例记录，以便保持数据准确性。

#### Acceptance Criteria

1. WHEN 医生在病例列表中点击"删除"按钮，THE System SHALL 显示二次确认对话框
2. WHEN 医生确认删除操作，THE System SHALL 检查该病例是否存在已签发的报告
3. WHEN 病例存在已签发报告，THE System SHALL 拒绝删除并提示"已签发报告的病例不可删除"
4. WHEN 病例不存在已签发报告，THE System SHALL 删除病例记录、关联的影像元数据记录以及 MinIO 中对应影像文件
5. WHEN 删除操作完成，THE System SHALL 在操作日志中记录删除操作、操作用户和被删除的病例 ID

### FR-8 与 PACS / HIS 对接预留

**User Story:** 作为放射科医生，我希望系统能够预留与 PACS 和 HIS 系统对接的能力，以便未来实现自动获取检查申请和影像数据。

#### Acceptance Criteria

1. WHEN 系统设计病例数据表结构，THE System SHALL 预留检查号、就诊号、科室代码等字段，以兼容 HIS 和 RIS 常用结构
2. WHEN 系统设计影像元数据表结构，THE System SHALL 预留 DICOM 路径、Study UID、Series UID、Instance UID 等字段
3. WHEN 系统管理员配置 PACS 对接参数，THE System SHALL 提供"影像共享目录路径"配置项并持久化存储
4. WHEN 系统管理员配置 HIS 对接参数，THE System SHALL 提供中间库连接信息和接口 URL 配置项并持久化存储
5. WHEN 系统接收到包含 DICOM UID 的影像记录，THE System SHALL 正确存储并关联到对应病例

### FR-9 病例列表性能要求

**User Story:** 作为放射科医生，我希望病例列表查询响应速度快，以便提高工作效率。

#### Acceptance Criteria

1. WHEN 医生访问病例列表页面，THE System SHALL 在 200 毫秒内返回查询结果（不含网络传输时间），在典型局域网环境下满足交互流畅性要求
2. WHEN 病例列表包含超过 100 条记录，THE System SHALL 采用分页加载策略，每页默认显示 20 条记录，并支持调整每页数量
3. WHEN 医生按检查时间、报告状态、签发医生等字段筛选，THE System SHALL 利用数据库索引在 200 毫秒内返回结果
4. WHEN 系统并发用户数达到 50 人，THE System SHALL 保证 99% 的病例列表查询请求响应时间小于 500 毫秒
5. WHEN 医生点击列表排序按钮，THE System SHALL 在 200 毫秒内返回排序后的结果

### FR-10 操作日志与审计

**User Story:** 作为系统管理员，我希望系统能够记录所有关键操作日志，以便进行审计和问题追踪。

#### Acceptance Criteria

1. WHEN 用户登录系统，THE System SHALL 在操作日志表中记录用户 ID、登录时间、客户端 IP 地址
2. WHEN 医生查看病例详情，THE System SHALL 在操作日志表中记录用户 ID、操作类型、病例 ID、操作时间
3. WHEN 医生上传影像文件，THE System SHALL 在操作日志表中记录用户 ID、操作类型、病例 ID、文件名、操作时间
4. WHEN 医生删除病例记录，THE System SHALL 在操作日志表中记录用户 ID、操作类型、被删除的病例 ID、操作时间
5. WHEN 系统管理员查询操作日志，THE System SHALL 支持按时间范围、用户、操作类型、目标对象等条件筛选，并按时间倒序展示结果
