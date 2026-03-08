# Design Document

## 1. Overview

本设计文档描述胸部 X 光智能辅助诊断系统中病例与影像管理模块的技术设计方案。该模块是系统的核心基础模块，负责管理胸部 X 光检查的结构化信息与影像资源，为后续 AI 分析与报告生成提供数据基础。

### 1.1 设计目标

- 提供高效的病例信息管理能力，支持快速检索和筛选
- 实现可靠的影像文件存储与访问机制
- 确保数据一致性和完整性
- 满足性能要求（病例列表查询 < 200ms）
- 预留与 PACS/HIS 系统对接的扩展能力
- 实现完整的操作审计追踪

### 1.2 技术栈

- **后端框架**: Spring Boot 3.x
- **持久层**: MyBatis-Plus + openGauss
- **对象存储**: MinIO
- **前端框架**: Vue 3 + Element Plus
- **认证授权**: JWT + RBAC
- **容器化**: Docker + Docker Compose
- **操作系统**: openEuler

## 2. Architecture

### 2.1 分层架构

系统采用经典的三层架构设计：

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│    (Vue 3 + Element Plus)               │
│  - 病例列表页面                          │
│  - 病例详情页面                          │
│  - 影像查看器                            │
│  - 典型病例管理                          │
└─────────────────────────────────────────┘
                    ↓ RESTful API (JWT)
┌─────────────────────────────────────────┐
│          Business Layer                 │
│       (Spring Boot Services)            │
│  - CaseService (病例管理)                │
│  - ImageService (影像管理)               │
│  - OperationLogService (日志审计)        │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          Data Layer                     │
│  - openGauss (结构化数据)                │
│  - MinIO (影像文件)                      │
└─────────────────────────────────────────┘
```

### 2.2 核心组件

#### 2.2.1 病例管理组件 (CaseService)

负责病例信息的 CRUD 操作、检索、筛选和典型病例标记。

#### 2.2.2 影像管理组件 (ImageService)

负责影像文件的上传、存储、检索和元数据管理。

#### 2.2.3 操作日志组件 (OperationLogService)

负责记录所有关键操作，提供审计查询能力。

### 2.3 前端组件结构

```
src/
├── views/
│   ├── CaseList.vue          # 病例列表页面
│   ├── CaseDetail.vue        # 病例详情页面
│   ├── TypicalCaseList.vue   # 典型病例列表
│   └── OperationLog.vue      # 操作日志页面
├── components/
│   ├── CaseTable.vue         # 病例表格组件
│   ├── CaseForm.vue          # 病例表单组件
│   ├── ImageViewer.vue       # 影像查看器组件
│   ├── ImageUploader.vue     # 影像上传组件
│   └── LogTable.vue          # 日志表格组件
├── api/
│   ├── case.js               # 病例 API
│   ├── image.js              # 影像 API
│   └── log.js                # 日志 API
├── stores/
│   ├── case.js               # 病例状态管理
│   └── user.js               # 用户状态管理
└── utils/
    ├── request.js            # Axios 封装
    └── auth.js               # JWT 处理
```

## 3. Data Models

### 3.1 病例表 (case_info)

```sql
CREATE TABLE case_info (
    case_id BIGINT PRIMARY KEY,
    exam_no VARCHAR(64) NOT NULL UNIQUE,
    patient_anon_id VARCHAR(64) NOT NULL,
    gender CHAR(1),
    age INT,
    exam_time TIMESTAMP NOT NULL,
    body_part VARCHAR(64) NOT NULL,
    department VARCHAR(64),
    report_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    is_typical TINYINT NOT NULL DEFAULT 0,
    typical_tags VARCHAR(256),
    typical_remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引设计
CREATE INDEX idx_exam_no ON case_info(exam_no);
CREATE INDEX idx_exam_time ON case_info(exam_time);
CREATE INDEX idx_patient_anon_id ON case_info(patient_anon_id);
CREATE INDEX idx_is_typical ON case_info(is_typical);
CREATE INDEX idx_report_status ON case_info(report_status);
```

**字段说明**:
- `case_id`: 病例唯一标识，使用雪花算法生成
- `exam_no`: 检查号，与 HIS/RIS 对接的关键字段
- `patient_anon_id`: 患者匿名 ID，保护隐私
- `report_status`: 报告状态（NONE=未生成, AI_DRAFT=AI草稿, EDITING=编辑中, SIGNED=已签发）
- `is_typical`: 是否典型病例标记（0=否，1=是）
- `typical_tags`: 典型病例标签，支持多标签（逗号分隔）

### 3.2 影像表 (image_info)

```sql
CREATE TABLE image_info (
    image_id BIGINT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    view_position VARCHAR(32),
    img_width INT,
    img_height INT,
    shoot_time TIMESTAMP,
    dicom_uid VARCHAR(128),
    study_uid VARCHAR(128),
    series_uid VARCHAR(128),
    instance_uid VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (case_id) REFERENCES case_info(case_id)
);

-- 索引设计
CREATE INDEX idx_case_id ON image_info(case_id);
CREATE INDEX idx_dicom_uid ON image_info(dicom_uid);
```

**字段说明**:
- `file_path`: MinIO 中的对象路径
- `file_size`: 文件大小（字节），用于上传前校验
- `dicom_uid`, `study_uid`, `series_uid`, `instance_uid`: DICOM 标准字段，预留 PACS 对接

### 3.3 操作日志表 (sys_operation_log)

```sql
CREATE TABLE sys_operation_log (
    log_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    operation_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64),
    detail TEXT,
    client_ip VARCHAR(64),
    api_path VARCHAR(256),
    elapsed_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
);

-- 索引设计
CREATE INDEX idx_user_id ON sys_operation_log(user_id);
CREATE INDEX idx_operation_type ON sys_operation_log(operation_type);
CREATE INDEX idx_created_at ON sys_operation_log(created_at);
```

**操作类型枚举**:
- `LOGIN`: 用户登录
- `CASE_VIEW`: 查看病例
- `CASE_CREATE`: 创建病例
- `CASE_UPDATE`: 更新病例
- `CASE_DELETE`: 删除病例
- `IMAGE_UPLOAD`: 上传影像
- `TYPICAL_MARK`: 标记典型病例


## 4. API Design

### 4.1 RESTful 接口规范

所有接口遵循统一的响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误响应格式：

```json
{
  "code": 400,
  "message": "检查号已存在，请检查是否重复录入",
  "data": null
}
```

### 4.2 病例管理接口

#### 4.2.1 查询病例列表

```
GET /api/cases
```

**请求参数**:
```json
{
  "examNo": "string (可选)",
  "patientAnonId": "string (可选)",
  "startTime": "datetime (可选)",
  "endTime": "datetime (可选)",
  "reportStatus": "string (可选)",
  "department": "string (可选)",
  "page": 1,
  "pageSize": 20,
  "sortBy": "exam_time",
  "sortOrder": "desc"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 150,
    "list": [
      {
        "caseId": 1234567890,
        "examNo": "EX20240301001",
        "patientAnonId": "P******01",
        "gender": "M",
        "age": 45,
        "examTime": "2024-03-01 10:30:00",
        "bodyPart": "胸部",
        "department": "放射科",
        "reportStatus": "AI_DRAFT",
        "isTypical": 0
      }
    ]
  }
}
```

#### 4.2.2 创建病例

```
POST /api/cases
```

**请求体**:
```json
{
  "examNo": "EX20240301001",
  "patientAnonId": "P123456",
  "gender": "M",
  "age": 45,
  "examTime": "2024-03-01 10:30:00",
  "bodyPart": "胸部",
  "department": "放射科"
}
```

**业务规则**:
- 检查号必须唯一
- 必填字段：examNo, patientAnonId, examTime, bodyPart
- 自动生成 caseId（雪花算法）

#### 4.2.3 更新病例

```
PUT /api/cases/{caseId}
```

**请求体**:
```json
{
  "patientAnonId": "P123456",
  "gender": "M",
  "age": 45,
  "department": "放射科"
}
```

#### 4.2.4 删除病例

```
DELETE /api/cases/{caseId}
```

**业务规则**:
- 检查是否存在已签发报告
- 级联删除关联的影像元数据
- 删除 MinIO 中的影像文件
- 记录操作日志

#### 4.2.5 标记/取消典型病例

```
POST /api/cases/{caseId}/typical
```

**请求体**:
```json
{
  "isTypical": 1,
  "typicalTags": "经典肺炎,教学案例",
  "typicalRemark": "典型的肺炎影像表现"
}
```

### 4.3 影像管理接口

#### 4.3.1 上传影像

```
POST /api/images/upload
```

**请求**: multipart/form-data
- `file`: 影像文件
- `caseId`: 所属病例 ID
- `viewPosition`: 投照体位（可选）

**业务流程**:
1. 校验文件大小（< 50MB）
2. 校验文件格式（JPG/PNG/DICOM）
3. 上传到 MinIO
4. 提取图像元数据（宽度、高度）
5. 创建 image_info 记录
6. 记录操作日志

**响应示例**:
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "imageId": 9876543210,
    "filePath": "cases/2024/03/01/xxx.jpg",
    "thumbnailUrl": "http://minio:9000/bucket/xxx_thumb.jpg"
  }
}
```

#### 4.3.2 查询病例影像列表

```
GET /api/images?caseId={caseId}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "imageId": 9876543210,
      "fileName": "chest_xray_001.jpg",
      "fileType": "JPG",
      "fileSize": 2048576,
      "viewPosition": "正位",
      "imgWidth": 2048,
      "imgHeight": 2048,
      "thumbnailUrl": "http://minio:9000/bucket/xxx_thumb.jpg",
      "fullUrl": "http://minio:9000/bucket/xxx.jpg"
    }
  ]
}
```

### 4.4 操作日志接口

#### 4.4.1 查询操作日志

```
GET /api/logs
```

**请求参数**:
```json
{
  "userId": "bigint (可选)",
  "operationType": "string (可选)",
  "startTime": "datetime (可选)",
  "endTime": "datetime (可选)",
  "page": 1,
  "pageSize": 20
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 500,
    "list": [
      {
        "logId": 1111111111,
        "userId": 100001,
        "userName": "张医生",
        "operationType": "CASE_DELETE",
        "targetId": "1234567890",
        "detail": "删除病例 EX20240301001",
        "clientIp": "192.168.1.100",
        "apiPath": "/api/cases/1234567890",
        "elapsedMs": 150,
        "createdAt": "2024-03-01 14:30:00"
      }
    ]
  }
}
```

### 4.5 批量导入接口

#### 4.5.1 批量导入病例

```
POST /api/cases/import
```

**请求**: multipart/form-data
- `file`: CSV 文件

**CSV 格式**:
```csv
检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室
EX20240301001,P123456,M,45,2024-03-01 10:30:00,胸部,放射科
```

**响应示例**:
```json
{
  "code": 200,
  "message": "导入完成",
  "data": {
    "totalRows": 100,
    "successCount": 95,
    "failedCount": 5,
    "errors": [
      {
        "row": 10,
        "reason": "检查号已存在"
      },
      {
        "row": 25,
        "reason": "必填字段缺失：检查时间"
      }
    ]
  }
}
```


## 5. Components and Interfaces

### 5.1 CaseService (病例服务)

**职责**: 病例信息的业务逻辑处理

**核心方法**:

```java
public interface CaseService {
    // 分页查询病例列表
    PageResult<CaseVO> listCases(CaseQueryDTO query);
    
    // 根据ID查询病例详情
    CaseDetailVO getCaseById(Long caseId);
    
    // 创建病例
    Long createCase(CaseCreateDTO dto);
    
    // 更新病例
    void updateCase(Long caseId, CaseUpdateDTO dto);
    
    // 删除病例（级联删除影像）
    void deleteCase(Long caseId);
    
    // 标记/取消典型病例
    void markTypical(Long caseId, TypicalMarkDTO dto);
    
    // 批量导入病例
    ImportResult importCases(MultipartFile file);
}
```

**关键业务逻辑**:

1. **创建病例**:
   - 校验检查号唯一性
   - 使用雪花算法生成 caseId
   - 记录操作日志

2. **删除病例**:
   - 检查是否存在已签发报告（通过 ReportService）
   - 查询关联的所有影像记录
   - 删除 MinIO 中的影像文件
   - 删除 image_info 记录
   - 删除 case_info 记录
   - 使用事务保证一致性

3. **查询优化**:
   - 利用索引加速查询
   - 分页查询避免全表扫描
   - 使用 MyBatis-Plus 的 LambdaQueryWrapper

### 5.2 ImageService (影像服务)

**职责**: 影像文件的上传、存储和管理

**核心方法**:

```java
public interface ImageService {
    // 上传影像
    ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition);
    
    // 查询病例的所有影像
    List<ImageVO> listImagesByCaseId(Long caseId);
    
    // 获取影像详情
    ImageDetailVO getImageById(Long imageId);
    
    // 删除影像
    void deleteImage(Long imageId);
    
    // 批量删除病例的所有影像
    void deleteImagesByCaseId(Long caseId);
}
```

**MinIO 集成**:

```java
@Service
public class ImageServiceImpl implements ImageService {
    
    @Autowired
    private MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Override
    public ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition) {
        // 1. 校验文件大小
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException("文件过大，请压缩或分批上传");
        }
        
        // 2. 校验文件格式
        String contentType = file.getContentType();
        if (!isValidImageType(contentType)) {
            throw new BusinessException("不支持的文件格式");
        }
        
        // 3. 生成对象路径
        String objectName = generateObjectName(caseId, file.getOriginalFilename());
        
        // 4. 上传到 MinIO
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new BusinessException("影像上传失败", e);
        }
        
        // 5. 提取图像元数据
        ImageMetadata metadata = extractImageMetadata(file);
        
        // 6. 保存到数据库
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setImageId(snowflakeIdGenerator.nextId());
        imageInfo.setCaseId(caseId);
        imageInfo.setFilePath(objectName);
        imageInfo.setFileName(file.getOriginalFilename());
        imageInfo.setFileType(getFileExtension(file.getOriginalFilename()));
        imageInfo.setFileSize(file.getSize());
        imageInfo.setViewPosition(viewPosition);
        imageInfo.setImgWidth(metadata.getWidth());
        imageInfo.setImgHeight(metadata.getHeight());
        imageInfoMapper.insert(imageInfo);
        
        // 7. 记录操作日志
        operationLogService.log("IMAGE_UPLOAD", caseId.toString(), 
            "上传影像: " + file.getOriginalFilename());
        
        return ImageUploadResult.builder()
            .imageId(imageInfo.getImageId())
            .filePath(objectName)
            .thumbnailUrl(generateThumbnailUrl(objectName))
            .build();
    }
    
    private String generateObjectName(Long caseId, String originalFilename) {
        LocalDate now = LocalDate.now();
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("cases/%d/%02d/%02d/%s.%s", 
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), uuid, extension);
    }
}
```

### 5.3 OperationLogService (操作日志服务)

**职责**: 记录和查询操作日志

**核心方法**:

```java
public interface OperationLogService {
    // 记录操作日志
    void log(String operationType, String targetId, String detail);
    
    // 分页查询操作日志
    PageResult<OperationLogVO> listLogs(LogQueryDTO query);
}
```

**AOP 实现自动日志记录**:

```java
@Aspect
@Component
public class OperationLogAspect {
    
    @Autowired
    private OperationLogService operationLogService;
    
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = System.currentTimeMillis() - startTime;
            
            // 记录日志
            SysOperationLog log = new SysOperationLog();
            log.setUserId(SecurityUtils.getCurrentUserId());
            log.setOperationType(operationLog.type());
            log.setTargetId(extractTargetId(joinPoint));
            log.setDetail(operationLog.detail());
            log.setClientIp(RequestUtils.getClientIp());
            log.setApiPath(RequestUtils.getRequestPath());
            log.setElapsedMs((int) elapsedMs);
            
            operationLogService.saveLog(log);
            
            return result;
        } catch (Exception e) {
            // 即使业务失败也记录日志
            operationLogService.logError(operationLog.type(), e.getMessage());
            throw e;
        }
    }
}
```

**使用示例**:

```java
@OperationLog(type = "CASE_DELETE", detail = "删除病例")
public void deleteCase(Long caseId) {
    // 业务逻辑
}
```

## 6. Error Handling

### 6.1 异常体系

```java
// 业务异常基类
public class BusinessException extends RuntimeException {
    private int code;
    private String message;
    
    public BusinessException(String message) {
        this(400, message);
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}

// 具体业务异常
public class CaseNotFoundException extends BusinessException {
    public CaseNotFoundException(Long caseId) {
        super(404, "病例不存在: " + caseId);
    }
}

public class DuplicateExamNoException extends BusinessException {
    public DuplicateExamNoException(String examNo) {
        super(400, "检查号已存在，请检查是否重复录入: " + examNo);
    }
}

public class CaseHasSignedReportException extends BusinessException {
    public CaseHasSignedReportException() {
        super(400, "已签发报告的病例不可删除");
    }
}
```

### 6.2 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return Result.error(400, message);
    }
    
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常，请联系管理员");
    }
}
```

## 7. Testing Strategy

### 7.1 单元测试

**CaseService 测试**:

```java
@SpringBootTest
class CaseServiceTest {
    
    @Autowired
    private CaseService caseService;
    
    @MockBean
    private CaseInfoMapper caseInfoMapper;
    
    @Test
    void testCreateCase_Success() {
        CaseCreateDTO dto = new CaseCreateDTO();
        dto.setExamNo("EX20240301001");
        dto.setPatientAnonId("P123456");
        dto.setExamTime(LocalDateTime.now());
        dto.setBodyPart("胸部");
        
        Long caseId = caseService.createCase(dto);
        
        assertNotNull(caseId);
        verify(caseInfoMapper).insert(any(CaseInfo.class));
    }
    
    @Test
    void testCreateCase_DuplicateExamNo() {
        when(caseInfoMapper.selectOne(any())).thenReturn(new CaseInfo());
        
        CaseCreateDTO dto = new CaseCreateDTO();
        dto.setExamNo("EX20240301001");
        
        assertThrows(DuplicateExamNoException.class, () -> {
            caseService.createCase(dto);
        });
    }
}
```

### 7.2 集成测试

**API 集成测试**:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CaseControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testListCases() throws Exception {
        mockMvc.perform(get("/api/cases")
                .param("page", "1")
                .param("pageSize", "20")
                .header("Authorization", "Bearer " + getTestToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list").isArray());
    }
}
```


## 8. Performance Optimization

### 8.1 数据库优化

**索引策略**:

```sql
-- 病例表核心索引
CREATE INDEX idx_exam_no ON case_info(exam_no);
CREATE INDEX idx_exam_time ON case_info(exam_time DESC);
CREATE INDEX idx_patient_anon_id ON case_info(patient_anon_id);
CREATE INDEX idx_is_typical ON case_info(is_typical) WHERE is_typical = 1;

-- 复合索引用于常见查询组合
CREATE INDEX idx_exam_time_status ON case_info(exam_time DESC, report_status);
CREATE INDEX idx_department_time ON case_info(department, exam_time DESC);

-- 影像表索引
CREATE INDEX idx_case_id ON image_info(case_id);
CREATE INDEX idx_dicom_uid ON image_info(dicom_uid);

-- 操作日志表索引
CREATE INDEX idx_user_id_time ON sys_operation_log(user_id, created_at DESC);
CREATE INDEX idx_operation_type_time ON sys_operation_log(operation_type, created_at DESC);
```

**分页查询优化**:

```java
// 使用 MyBatis-Plus 的分页插件
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.OPENGAUSS));
        return interceptor;
    }
}

// Service 层使用
public PageResult<CaseVO> listCases(CaseQueryDTO query) {
    Page<CaseInfo> page = new Page<>(query.getPage(), query.getPageSize());
    
    LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
    wrapper.like(StringUtils.isNotBlank(query.getExamNo()), 
                 CaseInfo::getExamNo, query.getExamNo())
           .like(StringUtils.isNotBlank(query.getPatientAnonId()), 
                 CaseInfo::getPatientAnonId, query.getPatientAnonId())
           .between(query.getStartTime() != null && query.getEndTime() != null,
                    CaseInfo::getExamTime, query.getStartTime(), query.getEndTime())
           .orderByDesc(CaseInfo::getExamTime);
    
    Page<CaseInfo> result = caseInfoMapper.selectPage(page, wrapper);
    
    return PageResult.of(result.getTotal(), 
                        result.getRecords().stream()
                              .map(this::convertToVO)
                              .collect(Collectors.toList()));
}
```

### 8.2 缓存策略

**Redis 缓存配置**:

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}

// 使用缓存
@Service
public class CaseServiceImpl implements CaseService {
    
    @Cacheable(value = "case", key = "#caseId")
    public CaseDetailVO getCaseById(Long caseId) {
        // 查询逻辑
    }
    
    @CacheEvict(value = "case", key = "#caseId")
    public void updateCase(Long caseId, CaseUpdateDTO dto) {
        // 更新逻辑
    }
}
```

### 8.3 MinIO 访问优化

**预签名 URL**:

```java
public String generatePresignedUrl(String objectName, int expirySeconds) {
    try {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .expiry(expirySeconds)
                .build()
        );
    } catch (Exception e) {
        throw new BusinessException("生成访问链接失败", e);
    }
}
```

**缩略图生成**:

```java
public void generateThumbnail(String originalPath, String thumbnailPath) {
    try {
        // 从 MinIO 下载原图
        InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .object(originalPath)
                .build()
        );
        
        // 生成缩略图
        BufferedImage original = ImageIO.read(inputStream);
        BufferedImage thumbnail = Thumbnails.of(original)
            .size(200, 200)
            .asBufferedImage();
        
        // 上传缩略图到 MinIO
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpg", os);
        
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .object(thumbnailPath)
                .stream(new ByteArrayInputStream(os.toByteArray()), 
                       os.size(), -1)
                .contentType("image/jpeg")
                .build()
        );
    } catch (Exception e) {
        log.error("生成缩略图失败", e);
    }
}
```

## 9. Security

### 9.1 JWT 认证

**JWT 配置**:

```java
@Configuration
public class JwtConfig {
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", ((CustomUserDetails) userDetails).getUserId());
        claims.put("roles", userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userDetails.getUsername())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
            .signWith(SignatureAlgorithm.HS512, secret)
            .compact();
    }
}
```

**JWT 过滤器**:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtConfig jwtConfig;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        
        if (token != null && jwtConfig.validateToken(token)) {
            String username = jwtConfig.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        chain.doFilter(request, response);
    }
}
```

### 9.2 RBAC 权限控制

**权限注解**:

```java
@RestController
@RequestMapping("/api/cases")
public class CaseController {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'QC', 'ADMIN')")
    public Result listCases(@Valid CaseQueryDTO query) {
        // 所有角色都可以查看病例列表
    }
    
    @DeleteMapping("/{caseId}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(type = "CASE_DELETE", detail = "删除病例")
    public Result deleteCase(@PathVariable Long caseId) {
        // 只有管理员可以删除病例
    }
}
```

### 9.3 数据脱敏

```java
public class DataMaskUtils {
    
    public static String maskPatientId(String patientId) {
        if (StringUtils.isBlank(patientId) || patientId.length() < 3) {
            return patientId;
        }
        return patientId.substring(0, 1) + "******" + 
               patientId.substring(patientId.length() - 2);
    }
}

// VO 中使用
public class CaseVO {
    private Long caseId;
    
    @JsonSerialize(using = PatientIdMaskSerializer.class)
    private String patientAnonId;  // 自动脱敏为 P******01
}
```

## 10. Deployment

### 10.1 Docker Compose 配置

```yaml
version: '3.8'

services:
  opengauss:
    image: enmotech/opengauss:latest
    container_name: chest-xray-db
    environment:
      GS_PASSWORD: ${DB_PASSWORD}
      GS_DB: chest_xray_db
    ports:
      - "5432:5432"
    volumes:
      - opengauss-data:/var/lib/opengauss
    networks:
      - chest-xray-network

  minio:
    image: minio/minio:latest
    container_name: chest-xray-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    networks:
      - chest-xray-network

  redis:
    image: redis:7-alpine
    container_name: chest-xray-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - chest-xray-network

  backend:
    build: ./backend
    container_name: chest-xray-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: opengauss
      DB_PORT: 5432
      DB_NAME: chest_xray_db
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_USER: ${MINIO_USER}
      MINIO_PASSWORD: ${MINIO_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
    ports:
      - "8080:8080"
    depends_on:
      - opengauss
      - minio
      - redis
    networks:
      - chest-xray-network

  frontend:
    build: ./frontend
    container_name: chest-xray-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - chest-xray-network

volumes:
  opengauss-data:
  minio-data:
  redis-data:

networks:
  chest-xray-network:
    driver: bridge
```

### 10.2 应用配置

**application-prod.yml**:

```yaml
server:
  port: 8080
  compression:
    enabled: true

spring:
  datasource:
    url: jdbc:opengauss://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: org.opengauss.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 2

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_USER}
  secret-key: ${MINIO_PASSWORD}
  bucket-name: chest-xray-images

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400  # 24小时

logging:
  level:
    root: INFO
    com.chestxray: DEBUG
  file:
    name: /var/log/chest-xray/application.log
    max-size: 100MB
    max-history: 30
```

## 11. Monitoring and Maintenance

### 11.1 健康检查

```java
@RestController
@RequestMapping("/actuator")
public class HealthController {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private MinioClient minioClient;
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("database", checkDatabase());
        health.put("minio", checkMinio());
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
    
    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
    
    private String checkMinio() {
        try {
            minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName).build());
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
```

### 11.2 性能监控

使用 Spring Boot Actuator + Prometheus + Grafana 进行监控。

**pom.xml**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**application.yml**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## 12. Future Enhancements

### 12.1 PACS 对接实现

当具备条件时，可实现以下功能：

1. **DICOM 接收服务**: 实现 DICOM C-STORE SCP，自动接收 PACS 推送的影像
2. **DICOM 查询服务**: 实现 DICOM C-FIND SCU，主动查询 PACS 中的检查记录
3. **Worklist 集成**: 与 RIS 的 Worklist 集成，自动获取检查申请

### 12.2 HIS 对接实现

1. **HL7 消息接口**: 接收 HIS 的 ADT、ORM 消息
2. **REST API 对接**: 通过 HTTP 接口与 HIS 交换数据
3. **中间库同步**: 定时从 HIS 中间库同步患者和检查信息
