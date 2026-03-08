# API 接口快速总览

## 已实现接口 (10个)

### 📋 病例管理 (7个)
1. `GET /api/cases` - 查询病例列表（支持多条件筛选、分页）
2. `GET /api/cases/{caseId}` - 查询病例详情
3. `POST /api/cases` - 创建病例
4. `PUT /api/cases/{caseId}` - 更新病例
5. `DELETE /api/cases/{caseId}` - 删除病例（级联删除影像）
6. `POST /api/cases/{caseId}/typical` - 标记/取消典型病例
7. `POST /api/cases/import` - 批量导入病例（CSV文件）✨ 新增

### 🖼️ 影像管理 (3个)
1. `POST /api/images/upload` - 上传影像（支持JPG/PNG/DICOM，最大50MB）
2. `GET /api/images?caseId={caseId}` - 查询病例的所有影像
3. `DELETE /api/images/{imageId}` - 删除影像

### 📊 操作日志 (1个)
1. `GET /api/logs` - 查询操作日志（需要管理员/质控权限）

---

## 快速测试

### Swagger UI
```
http://localhost:8080/doc.html
```

### 示例请求

#### 查询病例列表
```bash
curl -X GET "http://localhost:8080/api/cases?page=1&pageSize=20"
```

#### 创建病例
```bash
curl -X POST "http://localhost:8080/api/cases" \
  -H "Content-Type: application/json" \
  -d '{
    "examNo": "EX20240301001",
    "patientAnonId": "P123456",
    "examTime": "2024-03-01 10:30:00",
    "bodyPart": "胸部"
  }'
```

#### 上传影像
```bash
curl -X POST "http://localhost:8080/api/images/upload" \
  -F "file=@image.jpg" \
  -F "caseId=1234567890"
```

#### 批量导入
```bash
curl -X POST "http://localhost:8080/api/cases/import" \
  -F "file=@cases.csv"
```

---

## 详细文档
查看 [完整API文档](./API_LIST.md)
