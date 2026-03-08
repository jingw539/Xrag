# 胸部 X 光智能辅助诊断系统 (XRAG) - API 接口文档

## 概述

本文档列出系统全部 REST API 接口，覆盖用户认证、病例管理、影像管理、RAG检索、报告生成、CheXbert评测、危急值预警、术语规范化、质控统计等所有模块。

**基础 URL**: `http://localhost:8080`

**认证方式**: JWT Bearer Token (`Authorization: Bearer {token}`)

**统一响应格式**:
```json
{ "code": 200, "message": "success", "data": { ... } }
```

**常见错误码**: `400` 参数错误 | `401` 未认证 | `403` 无权限 | `404` 不存在 | `500` 服务器错误

---

## 1. 用户认证 (AuthController)

### 1.1 用户登录
- **接口**: `POST /api/auth/login`
- **描述**: 账号密码登录，返回 Access Token 和 Refresh Token
- **权限**: 无需认证
- **请求体**:
  ```json
  { "username": "doctor01", "password": "Abc@12345" }
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "accessToken": "eyJhbGci...",
      "refreshToken": "dGhpcyBp...",
      "expiresIn": 7200,
      "userInfo": { "userId": 100001, "username": "doctor01", "realName": "张医生", "roleCode": "DOCTOR", "department": "放射科" }
    }
  }
  ```

### 1.2 刷新 Token
- **接口**: `POST /api/auth/refresh`
- **描述**: 使用 Refresh Token 获取新的 Access Token
- **权限**: 无需认证
- **请求体**:
  ```json
  { "refreshToken": "dGhpcyBp..." }
  ```
- **响应示例**:
  ```json
  { "code": 200, "message": "success", "data": { "accessToken": "eyJhbGci...", "expiresIn": 7200 } }
  ```

### 1.3 用户登出
- **接口**: `POST /api/auth/logout`
- **描述**: 吊销当前 Refresh Token，使会话失效
- **权限**: 已登录用户
- **响应示例**:
  ```json
  { "code": 200, "message": "已登出", "data": null }
  ```

### 1.4 获取当前用户信息
- **接口**: `GET /api/auth/me`
- **描述**: 获取当前登录用户的详细信息
- **权限**: 已登录用户
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": { "userId": 100001, "username": "doctor01", "realName": "张医生", "roleCode": "DOCTOR", "department": "放射科", "status": 1, "lastLoginAt": "2024-03-01 09:00:00" }
  }
  ```

---

## 2. 用户管理 (UserController)

### 2.1 查询用户列表
- **接口**: `GET /api/users`
- **描述**: 分页查询用户列表
- **权限**: ADMIN
- **请求参数**:
  ```
  username: string (可选) - 账号，模糊查询
  realName: string (可选) - 姓名，模糊查询
  roleCode: string (可选) - 角色编码 (DOCTOR/QC/ADMIN)
  department: string (可选) - 科室
  status: integer (可选) - 状态 (1=启用，0=禁用)
  page: integer (默认1)
  pageSize: integer (默认20)
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "total": 30,
      "list": [{ "userId": 100001, "username": "doctor01", "realName": "张医生", "roleCode": "DOCTOR", "department": "放射科", "status": 1, "lastLoginAt": "2024-03-01 09:00:00", "createdAt": "2024-01-01 08:00:00" }]
    }
  }
  ```

### 2.2 查询用户详情
- **接口**: `GET /api/users/{userId}`
- **权限**: ADMIN 或本人
- **响应**: 同上单条数据

### 2.3 创建用户
- **接口**: `POST /api/users`
- **权限**: ADMIN
- **请求体**:
  ```json
  { "username": "doctor02", "password": "Abc@12345", "realName": "李医生", "roleCode": "DOCTOR", "department": "放射科" }
  ```
- **响应**: `{ "code": 200, "data": 100002 }` (返回新用户ID)

### 2.4 更新用户信息
- **接口**: `PUT /api/users/{userId}`
- **权限**: ADMIN 或本人（本人仅可改姓名、科室）
- **请求体**:
  ```json
  { "realName": "李主任", "department": "急诊科", "roleCode": "DOCTOR" }
  ```

### 2.5 修改密码
- **接口**: `PUT /api/users/{userId}/password`
- **权限**: ADMIN 或本人
- **请求体**:
  ```json
  { "oldPassword": "Abc@12345", "newPassword": "Xyz@67890" }
  ```
- **业务规则**: 本人操作需校验旧密码；ADMIN可直接重置

### 2.6 启用/禁用用户
- **接口**: `PUT /api/users/{userId}/status`
- **权限**: ADMIN
- **请求体**: `{ "status": 0 }` (0=禁用，1=启用)

### 2.7 删除用户
- **接口**: `DELETE /api/users/{userId}`
- **权限**: ADMIN
- **业务规则**: 不可删除有签发报告的医生账号

---

## 3. 病例管理 (CaseController)

### 3.1 查询病例列表
- **接口**: `GET /api/cases`
- **权限**: 所有已登录用户
- **请求参数**:
  ```
  examNo: string (可选) - 检查号，模糊查询
  patientAnonId: string (可选) - 患者匿名ID，模糊查询
  startTime: datetime (可选) - 检查时间起
  endTime: datetime (可选) - 检查时间止
  reportStatus: string (可选) - NONE/AI_DRAFT/EDITING/SIGNED
  department: string (可选)
  isTypical: integer (可选) - 0/1
  page: integer (默认1)
  pageSize: integer (默认20)
  sortOrder: string (默认desc)
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "total": 150,
      "list": [{ "caseId": 1234567890, "examNo": "EX20240301001", "patientAnonId": "P******01", "gender": "M", "age": 45, "examTime": "2024-03-01 10:30:00", "bodyPart": "胸部", "department": "放射科", "reportStatus": "AI_DRAFT", "isTypical": 0 }]
    }
  }
  ```

### 3.2 查询病例详情
- **接口**: `GET /api/cases/{caseId}`
- **权限**: 所有已登录用户
- **响应**: 含 `typicalTags`、`typicalRemark`、`createdAt`、`updatedAt` 完整字段

### 3.3 创建病例
- **接口**: `POST /api/cases`
- **权限**: DOCTOR、ADMIN
- **请求体**:
  ```json
  { "examNo": "EX20240301001", "patientAnonId": "P123456", "gender": "M", "age": 45, "examTime": "2024-03-01 10:30:00", "bodyPart": "胸部", "department": "放射科" }
  ```

### 3.4 更新病例
- **接口**: `PUT /api/cases/{caseId}`
- **权限**: DOCTOR、ADMIN
- **请求体**: 支持更新 `patientAnonId`、`gender`、`age`、`department`

### 3.5 删除病例
- **接口**: `DELETE /api/cases/{caseId}`
- **权限**: ADMIN
- **业务规则**: 已签发报告的病例不可删除；级联删除影像文件

### 3.6 标记/取消典型病例
- **接口**: `POST /api/cases/{caseId}/typical`
- **权限**: DOCTOR、QC、ADMIN
- **请求体**:
  ```json
  { "isTypical": 1, "typicalTags": "经典气胸,教学案例", "typicalRemark": "右侧自发性气胸，典型影像表现" }
  ```

### 3.7 批量导入病例
- **接口**: `POST /api/cases/import`
- **权限**: ADMIN
- **请求类型**: `multipart/form-data`，`file`: CSV文件
- **CSV格式**: `检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室`
- **响应**: `{ "totalRows": 100, "successCount": 95, "failedCount": 5, "errors": [{ "row": 10, "reason": "检查号已存在" }] }`

---

## 4. 影像管理 (ImageController)

### 4.1 上传影像
- **接口**: `POST /api/images/upload`
- **权限**: DOCTOR、ADMIN
- **请求类型**: `multipart/form-data`
- **参数**: `file`(必填，JPG/PNG/DCM，≤50MB)、`caseId`(必填)、`viewPosition`(可选，PA/AP/LATERAL/OBLIQUE)
- **响应**:
  ```json
  {
    "code": 200, "message": "上传成功",
    "data": {
      "imageId": 9876543210,
      "filePath": "cases/2024/03/01/abc123.jpg",
      "thumbnailUrl": "http://minio:9000/xrag/cases/2024/03/01/abc123_thumb.jpg",
      "dicomMetadata": { "pixelSpacing": "0.143\\0.143", "windowCenter": 400, "windowWidth": 1500 }
    }
  }
  ```

### 4.2 查询病例影像列表
- **接口**: `GET /api/images`
- **权限**: 所有已登录用户
- **请求参数**: `caseId`(必填)
- **响应**: 含 `imageId`、`fileName`、`fileType`、`fileSize`、`viewPosition`、`imgWidth`、`imgHeight`、`shootTime`、`pixelSpacing`、`windowCenter`、`windowWidth`、`thumbnailUrl`、`fullUrl`

### 4.3 获取影像详情
- **接口**: `GET /api/images/{imageId}`
- **权限**: 所有已登录用户
- **响应**: 完整影像信息含所有DICOM元数据字段

### 4.4 删除影像
- **接口**: `DELETE /api/images/{imageId}`
- **权限**: DOCTOR、ADMIN
- **业务规则**: 同步删除MinIO文件与数据库记录

---

## 5. RAG检索 (RetrievalController)

### 5.1 执行相似病例检索
- **接口**: `POST /api/retrieval/search`
- **描述**: 使用 MedCLIP 对指定影像进行 FAISS 向量检索，返回 Top-K 相似病例
- **权限**: DOCTOR、ADMIN
- **请求体**:
  ```json
  { "caseId": 1234567890, "imageId": 9876543210, "topK": 3 }
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "retrievalId": 5555555555,
      "elapsedMs": 320,
      "allAboveThreshold": true,
      "results": [
        {
          "rank": 1,
          "caseId": 9900001,
          "examNo": "EX20230815003",
          "similarityScore": 0.921,
          "chexpertLabels": ["Pneumothorax", "No Finding"],
          "reportSummary": "Right-sided pneumothorax with partial lung collapse...",
          "thumbnailUrl": "http://minio:9000/xrag/cases/..."
        }
      ]
    }
  }
  ```
- **业务规则**: 若所有相似度 < `similarity_threshold`(0.85)，返回警告提示建议人工书写

### 5.2 查询检索记录
- **接口**: `GET /api/retrieval/{caseId}/latest`
- **描述**: 获取指定病例最近一次的检索结果
- **权限**: DOCTOR、QC、ADMIN
- **响应**: 含 `retrievalId`、`similarCaseIds`、`similarityScores`、`elapsedMs`、`retrievalTime`

### 5.3 查询检索历史列表
- **接口**: `GET /api/retrieval/{caseId}/history`
- **描述**: 查看指定病例全部检索历史（支持分页）
- **权限**: DOCTOR、QC、ADMIN
- **请求参数**: `page`、`pageSize`

---

## 6. 报告管理 (ReportController)

### 6.1 触发 AI 报告生成
- **接口**: `POST /api/reports/generate`
- **描述**: 基于 RAG 检索结果调用 LLaVA-Med-LoRA 生成结构化报告草稿，同步触发 CheXbert 评测
- **权限**: DOCTOR、ADMIN
- **请求体**:
  ```json
  { "caseId": 1234567890, "imageId": 9876543210, "retrievalId": 5555555555 }
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "reportId": 7777777777,
      "reportStatus": "AI_DRAFT",
      "aiFindings": "The lungs are clear bilaterally. No focal consolidation, pleural effusion, or pneumothorax identified...",
      "aiImpression": "No acute cardiopulmonary disease.",
      "qualityGrade": "A",
      "modelConfidence": 0.8731,
      "elapsedMs": 8240,
      "evalResult": { "f1Score": 0.83, "qualityGrade": "A", "missingLabels": [], "extraLabels": [] }
    }
  }
  ```

### 6.2 查询病例报告
- **接口**: `GET /api/reports?caseId={caseId}`
- **权限**: 所有已登录用户
- **响应**: 含完整报告信息及关联检索记录摘要

### 6.3 查询报告详情
- **接口**: `GET /api/reports/{reportId}`
- **权限**: 所有已登录用户
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "reportId": 7777777777,
      "caseId": 1234567890,
      "reportStatus": "EDITING",
      "genModelId": 3,
      "retrievalLogId": 5555555555,
      "aiFindings": "...",
      "aiImpression": "...",
      "finalFindings": "...",
      "finalImpression": "...",
      "qualityGrade": "A",
      "modelConfidence": 0.8731,
      "doctorId": null,
      "signTime": null,
      "aiGenerateTime": "2024-03-01 10:45:00",
      "createdAt": "2024-03-01 10:45:00",
      "updatedAt": "2024-03-01 11:00:00"
    }
  }
  ```

### 6.4 保存报告草稿
- **接口**: `PUT /api/reports/{reportId}/draft`
- **描述**: 医生编辑并保存报告草稿，记录修改历史
- **权限**: DOCTOR、ADMIN
- **请求体**:
  ```json
  { "finalFindings": "修改后的影像所见...", "finalImpression": "修改后的影像印象...", "editNote": "补充右肺下叶描述" }
  ```
- **业务规则**: 自动写入 `report_edit_history`，状态变更为 `EDITING`

### 6.5 签发报告
- **接口**: `POST /api/reports/{reportId}/sign`
- **描述**: 医生审核通过后签发报告，状态变为 `SIGNED`
- **权限**: DOCTOR、ADMIN
- **请求体**: `{ "finalFindings": "最终影像所见...", "finalImpression": "最终影像印象..." }`
- **业务规则**: 签发后不可再修改；同步更新 `case_info.report_status = SIGNED`；触发一次 FINAL 类型 CheXbert 评测

### 6.6 重新生成 AI 报告
- **接口**: `POST /api/reports/{reportId}/regenerate`
- **描述**: 已签发以外的报告均可重新触发 AI 生成（使用最新检索结果）
- **权限**: DOCTOR、ADMIN
- **响应**: 同 6.1

### 6.7 查询报告修改历史
- **接口**: `GET /api/reports/{reportId}/history`
- **描述**: 获取报告全部修改记录，满足审核留痕合规要求
- **权限**: DOCTOR、QC、ADMIN
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": [
      {
        "historyId": 8888888888,
        "editorId": 100001,
        "editorName": "张医生",
        "findingsBefore": "原始AI所见...",
        "findingsAfter": "修改后所见...",
        "impressionBefore": "原始AI印象...",
        "impressionAfter": "修改后印象...",
        "editNote": "补充右肺下叶描述",
        "editTime": "2024-03-01 11:00:00"
      }
    ]
  }
  ```

---

## 7. CheXbert 评测 (EvalController)

### 7.1 手动触发评测
- **接口**: `POST /api/eval/trigger`
- **描述**: 对指定报告手动触发 CheXbert 14类标签提取与一致性评测
- **权限**: DOCTOR、QC、ADMIN
- **请求体**:
  ```json
  { "reportId": 7777777777, "evalType": "AI" }
  ```
  - `evalType`: `AI`=评测AI草稿，`FINAL`=评测最终报告
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "evalId": 6666666666,
      "f1Score": 0.83,
      "precisionScore": 0.87,
      "recallScore": 0.79,
      "qualityGrade": "A",
      "bleu4Score": 0.41,
      "rougeL Score": 0.58,
      "missingLabels": [],
      "extraLabels": [],
      "perLabelF1": { "Atelectasis": 0.90, "Cardiomegaly": 0.85, "Pneumothorax": 0.78, "No Finding": 0.95 },
      "elapsedMs": 1820
    }
  }
  ```

### 7.2 查询报告评测结果
- **接口**: `GET /api/eval/{reportId}`
- **描述**: 获取指定报告的最新评测结果（含14类标签明细）
- **权限**: 所有已登录用户
- **响应**: 含完整 `eval_result` 数据及 `aiLabels`、`refLabels` JSON

### 7.3 查询评测统计（质控面板）
- **接口**: `GET /api/eval/statistics`
- **描述**: 按时间段统计 CheXbert F1 趋势与质量等级分布
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`、`modelId`(可选)、`evalType`(AI/FINAL，可选)
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "avgF1": 0.79,
      "gradeDistribution": { "A": 42, "B": 35, "C": 18, "D": 5 },
      "f1Trend": [
        { "date": "2024-03-01", "avgF1": 0.81 },
        { "date": "2024-03-02", "avgF1": 0.78 }
      ],
      "topMissingLabels": [{ "label": "Pleural Effusion", "missCount": 12 }]
    }
  }
  ```

### 7.4 查询每类标签 F1 统计
- **接口**: `GET /api/eval/per-label`
- **描述**: 汇总14类标签各自的平均精确率、召回率和F1，用于识别模型薄弱标签
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`
- **响应**: 14类标签的 `precision`、`recall`、`f1` 统计列表

---

## 8. 危急值预警 (AlertController)

### 8.1 查询预警列表
- **接口**: `GET /api/alerts`
- **描述**: 查询危急值预警记录，支持分页和状态筛选
- **权限**: DOCTOR、QC、ADMIN
- **请求参数**:
  ```
  alertStatus: string (可选) - PENDING/ACKNOWLEDGED/ESCALATED
  labelType: string (可选) - 病理标签类型 (如Pneumothorax)
  startTime: datetime (可选)
  endTime: datetime (可选)
  page: integer (默认1)
  pageSize: integer (默认20)
  ```
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "total": 25,
      "pendingCount": 3,
      "list": [
        {
          "alertId": 4444444444,
          "caseId": 1234567890,
          "examNo": "EX20240301005",
          "reportId": 7777777777,
          "labelType": "Pneumothorax",
          "labelProb": 0.921,
          "alertStatus": "PENDING",
          "alertTime": "2024-03-01 10:46:00"
        }
      ]
    }
  }
  ```

### 8.2 查询预警详情
- **接口**: `GET /api/alerts/{alertId}`
- **权限**: DOCTOR、QC、ADMIN
- **响应**: 含完整预警信息、关联病例和报告摘要

### 8.3 处理预警（确认/上转/驳回）
- **接口**: `PUT /api/alerts/{alertId}/respond`
- **描述**: 医师响应危急值预警，记录处理动作和时间
- **权限**: DOCTOR、ADMIN
- **请求体**:
  ```json
  { "responseAction": "ACKNOWLEDGED", "responseNote": "已通知临床医生，患者已安排急诊处理" }
  ```
  - `responseAction`: `ACKNOWLEDGED`=确认知晓 | `ESCALATED`=上转处理 | `DISMISSED`=误报驳回
- **业务规则**: 更新 `alert_status` 和 `response_time`；写入操作日志

### 8.4 预警统计
- **接口**: `GET /api/alerts/statistics`
- **描述**: 统计各类危急值的响应时效、处理率等质控指标
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "totalAlerts": 50,
      "pendingCount": 3,
      "avgResponseMinutes": 12.5,
      "byLabel": [
        { "labelType": "Pneumothorax", "count": 20, "avgResponseMinutes": 8.3 },
        { "labelType": "Pleural Effusion", "count": 30, "avgResponseMinutes": 15.2 }
      ]
    }
  }
  ```

---

## 9. 术语规范化 (TermController)

### 9.1 查询报告术语校正建议
- **接口**: `GET /api/terms/{reportId}`
- **描述**: 获取指定报告的所有术语校正建议列表
- **权限**: DOCTOR、QC、ADMIN
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": [
      {
        "correctionId": 3333333333,
        "originalTerm": "渗出",
        "suggestedTerm": "实变（Consolidation）",
        "contextSentence": "右肺下叶可见渗出性改变...",
        "isAccepted": 0
      }
    ]
  }
  ```

### 9.2 采纳/忽略术语建议
- **接口**: `PUT /api/terms/{correctionId}`
- **描述**: 医师选择采纳或忽略某条术语校正建议
- **权限**: DOCTOR、ADMIN
- **请求体**: `{ "isAccepted": 1 }`

---

## 10. 质控统计 (StatisticsController)

### 10.1 质控 Dashboard
- **接口**: `GET /api/statistics/dashboard`
- **描述**: 汇总系统核心质控指标，用于管理大屏展示
- **权限**: QC、ADMIN
- **请求参数**: `startTime`(可选)、`endTime`(可选)
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "totalCases": 1250,
      "signedReports": 980,
      "avgAiF1": 0.79,
      "pendingAlerts": 3,
      "avgReportGenerateMs": 8200,
      "gradeDistribution": { "A": 42, "B": 35, "C": 18, "D": 5 },
      "reportAdoptionRate": 0.72
    }
  }
  ```

### 10.2 报告质量趋势统计
- **接口**: `GET /api/statistics/report-quality`
- **描述**: 按日/周统计报告生成量、质量等级分布、F1趋势
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`、`granularity`(DAY/WEEK，默认DAY)

### 10.3 医生工作量统计
- **接口**: `GET /api/statistics/doctor-performance`
- **描述**: 统计各医生的报告审核量、平均改动率、签发时效
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": [
      { "doctorId": 100001, "realName": "张医生", "signedCount": 320, "avgEditRate": 0.28, "avgSignMinutes": 4.5 }
    ]
  }
  ```

### 10.4 AI 性能统计
- **接口**: `GET /api/statistics/ai-performance`
- **描述**: 统计AI模型推理耗时、RAG检索耗时、CheXbert评测耗时趋势
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`、`modelId`(可选)
- **响应**: 含 `avgGenerateMs`、`avgRetrievalMs`、`avgEvalMs`、`p95GenerateMs` 等时效指标

### 10.5 检索质量统计
- **接口**: `GET /api/statistics/retrieval-quality`
- **描述**: 统计RAG检索命中率（超阈值比例）、平均相似度分布、检索耗时趋势
- **权限**: QC、ADMIN
- **请求参数**: `startTime`、`endTime`

---

## 11. 系统配置 (ConfigController)

### 11.1 查询配置列表
- **接口**: `GET /api/config`
- **权限**: ADMIN
- **响应**: 全部配置项列表（`configId`、`configKey`、`configValue`、`description`、`updatedAt`）

### 11.2 查询单项配置
- **接口**: `GET /api/config/{configKey}`
- **权限**: ADMIN
- **示例**: `GET /api/config/retrieval_top_k`

### 11.3 更新配置
- **接口**: `PUT /api/config/{configKey}`
- **权限**: ADMIN
- **请求体**: `{ "configValue": "5" }`
- **业务规则**: 写入操作日志；部分配置（如模型路径）更新后需重启服务生效

---

## 12. 数据字典 (DictController)

### 12.1 查询字典项
- **接口**: `GET /api/dict/{dictCode}`
- **描述**: 获取指定字典编码下的所有枚举项（按 `sort_order` 排序）
- **权限**: 所有已登录用户
- **示例**: `GET /api/dict/PATHOLOGY_LABEL`
- **响应**:
  ```json
  {
    "code": 200, "message": "success",
    "data": [
      { "itemCode": "Atelectasis", "itemName": "肺不张", "sortOrder": 1 },
      { "itemCode": "Cardiomegaly", "itemName": "心脏肥大", "sortOrder": 2 }
    ]
  }
  ```

### 12.2 查询所有字典
- **接口**: `GET /api/dict`
- **描述**: 获取全部字典分类及其枚举项（一次性加载前端缓存用）
- **权限**: 所有已登录用户

---

## 13. 操作日志 (OperationLogController)

### 13.1 查询操作日志
- **接口**: `GET /api/logs`
- **权限**: QC、ADMIN
- **请求参数**:
  ```
  userId: long (可选)
  operationType: string (可选)
  targetType: string (可选) - CASE/IMAGE/REPORT/USER/CONFIG/ALERT
  startTime: datetime (可选)
  endTime: datetime (可选)
  page: integer (默认1)
  pageSize: integer (默认20)
  ```
- **操作类型完整枚举**:
  `LOGIN` / `LOGOUT` / `CASE_VIEW` / `CASE_CREATE` / `CASE_UPDATE` / `CASE_DELETE` / `IMAGE_UPLOAD` / `IMAGE_DELETE` / `REPORT_GENERATE` / `REPORT_EDIT` / `REPORT_SIGN` / `REPORT_REGENERATE` / `RETRIEVAL` / `EVAL_TRIGGER` / `ALERT_ACK` / `TYPICAL_MARK` / `CONFIG_UPDATE` / `USER_CREATE` / `USER_UPDATE`
- **响应示例**:
  ```json
  {
    "code": 200, "message": "success",
    "data": {
      "total": 5000,
      "list": [{ "logId": 1111111111, "userId": 100001, "userName": "张医生", "operationType": "REPORT_SIGN", "targetType": "REPORT", "targetId": "7777777777", "detail": "签发报告 EX20240301001", "clientIp": "192.168.1.100", "apiPath": "/api/reports/7777777777/sign", "elapsedMs": 210, "createdAt": "2024-03-01 11:10:00" }]
    }
  }
  ```

---

## 接口汇总

| 模块 | 接口数 | 主要功能 |
|------|--------|---------|
| 用户认证 | 4 | 登录、登出、刷新Token、获取当前用户 |
| 用户管理 | 7 | 用户CRUD、密码修改、状态切换 |
| 病例管理 | 7 | 病例CRUD、典型标记、批量导入 |
| 影像管理 | 4 | 上传、列表、详情、删除 |
| RAG检索 | 3 | 执行检索、查询最新结果、检索历史 |
| 报告管理 | 7 | AI生成、草稿保存、签发、重新生成、修改历史 |
| CheXbert评测 | 4 | 触发评测、查询结果、统计、标签明细 |
| 危急值预警 | 4 | 列表、详情、响应处理、统计 |
| 术语规范化 | 2 | 查询建议、采纳/忽略 |
| 质控统计 | 5 | Dashboard、质量趋势、医生工作量、AI性能、检索质量 |
| 系统配置 | 3 | 列表、查询、更新 |
| 数据字典 | 2 | 按分类查询、全量查询 |
| 操作日志 | 1 | 多条件分页查询 |
| **合计** | **53** | |

---

## Swagger UI

启动应用后访问：`http://localhost:8080/doc.html`

---

## 更新日志

- **v1.0 (2024-03-04)**: 初始版本，病例管理、影像管理、操作日志共10个接口
- **v2.0 (2024-03-04)**: 全面扩充，新增认证、用户管理、RAG检索、报告管理、CheXbert评测、危急值预警、术语规范化、质控统计等模块，接口总数扩充至53个
