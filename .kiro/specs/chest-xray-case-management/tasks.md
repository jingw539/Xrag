
- [ ] 6. 批量导入功能实现



- [x] 6.1 实现 CSV 文件解析



  - 实现 CaseService.importCases() 方法
  - 实现 CSV 文件格式校验
  - 实现批量数据插入逻辑
  - 实现错误收集和报告生成
  - _Requirements: FR-6_

- [x] 6.2 实现批量导入接口




  - 实现 CaseController.importCases() 接口
  - 实现文件上传处理
  - 实现导入结果返回
  - 编写单元测试
  - _Requirements: FR-6_

- [x] 7. 异常处理与统一响应



- [x] 7.1 创建业务异常类



  - 创建 BusinessException 基类
  - 创建 CaseNotFoundException 异常
  - 创建 DuplicateExamNoException 异常
  - 创建 CaseHasSignedReportException 异常
  - _Requirements: FR-1, FR-4, FR-7_



- [x] 7.2 实现全局异常处理器

  - 创建 GlobalExceptionHandler
  - 实现 BusinessException 处理
  - 实现 MethodArgumentNotValidException 处理
  - 实现通用 Exception 处理
  - _Requirements: FR-1, FR-2, FR-4_

- [x] 8. 性能优化实现



- [x] 8.1 配置 MyBatis-Plus 分页插件


  - 配置 PaginationInnerInterceptor
  - 优化分页查询 SQL
  - _Requirements: FR-9_

- [x] 8.2 配置 Redis 缓存



  - 配置 RedisCacheManager
  - 为 getCaseById() 添加 @Cacheable
  - 为 updateCase() 添加 @CacheEvict
  - _Requirements: FR-9_

- [ ] 9. 前端病例管理页面实现
- [ ] 9.1 实现病例列表页面
  - 创建 CaseList.vue 组件
  - 实现病例表格展示
  - 实现多条件筛选表单
  - 实现分页组件
  - 调用后端 API 获取数据
  - _Requirements: FR-1_

- [ ] 9.2 实现病例详情页面
  - 创建 CaseDetail.vue 组件
  - 实现病例信息展示
  - 实现影像列表展示
  - 实现编辑和删除按钮
  - _Requirements: FR-1, FR-4_

- [ ] 9.3 实现病例表单组件
  - 创建 CaseForm.vue 组件
  - 实现表单验证
  - 实现新建和编辑模式
  - 调用后端 API 提交数据
  - _Requirements: FR-4_

- [ ] 10. 前端影像管理功能实现
- [ ] 10.1 实现影像上传组件
  - 创建 ImageUploader.vue 组件
  - 实现文件选择和预览
  - 实现上传进度显示
  - 实现文件大小和格式校验
  - 调用后端 API 上传文件
  - _Requirements: FR-2_

- [ ] 10.2 实现影像查看器组件
  - 创建 ImageViewer.vue 组件
  - 实现影像加载和显示
  - 实现缩放、平移、旋转功能
  - 实现窗宽窗位调节
  - _Requirements: FR-3_

- [ ] 11. 前端典型病例与日志功能
- [ ] 11.1 实现典型病例列表页面
  - 创建 TypicalCaseList.vue 组件
  - 实现典型病例筛选
  - 实现标记和取消标记功能
  - _Requirements: FR-5_

- [ ] 11.2 实现操作日志页面
  - 创建 OperationLog.vue 组件
  - 实现日志表格展示
  - 实现多条件筛选
  - _Requirements: FR-10_

- [ ] 12. 集成测试与部署
- [ ] 12.1 编写 API 集成测试
  - 编写病例管理接口集成测试
  - 编写影像管理接口集成测试
  - 编写操作日志接口集成测试
  - _Requirements: FR-1, FR-2, FR-10_

- [ ] 12.2 配置生产环境部署
  - 编写 Dockerfile（后端和前端）
  - 配置 application-prod.yml
  - 编写部署文档
  - _Requirements: FR-9_

- [ ] 12.3 配置监控和健康检查
  - 实现 /actuator/health 端点
  - 配置 Prometheus 指标导出
  - 编写监控文档
  - _Requirements: FR-9_
