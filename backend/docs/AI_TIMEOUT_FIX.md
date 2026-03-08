# AI API 超时问题修复说明

## 问题描述

在调用外部AI API（Qwen-VL-Plus、DeepSeek）生成报告时，由于AI模型处理图像和生成文本需要较长时间，导致请求超时（30秒）。

## 修复内容

### 1. 后端 RestTemplate 超时配置

**文件**: `backend/src/main/java/com/hospital/xray/config/RestTemplateConfig.java`

**修改**:
- 连接超时：10秒
- 读取超时：120秒（适配AI API的长时间处理）

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(120))
            .build();
}
```

### 2. AI API 配置超时参数

**文件**: `backend/src/main/resources/application-dev.yml`

**新增配置**:
```yaml
ai:
  qwen:
    timeout: 120000  # 120秒（毫秒）
  deepseek:
    timeout: 60000   # 60秒（毫秒）
```

### 3. 前端 Axios 超时配置

**文件**: `frontend/src/utils/request.js`

**修改**:
- 全局超时：从30秒增加到150秒
- 新增超时错误处理
- 新增504错误提示

```javascript
const request = axios.create({
  baseURL: '/api',
  timeout: 150000, // 150秒
  // ...
})
```

### 4. 优化错误提示

**后端** (`AiServiceClient.java`):
- 添加详细的日志记录（开始时间、结束时间、耗时）
- 区分超时错误和其他网络错误
- 返回更友好的错误提示

**前端** (`request.js`):
- 检测 `ECONNABORTED` 超时错误
- 显示"AI服务处理时间较长，请稍后重试"
- 处理504网关超时错误

## 使用建议

### 1. 环境变量配置

确保在 `.env` 或环境变量中配置了有效的API密钥：

```bash
QWEN_API_KEY=sk-your-qwen-api-key
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
```

### 2. 网络环境

- 确保服务器能够访问外部AI API（dashscope.aliyuncs.com、api.deepseek.com）
- 如果在内网环境，需要配置代理或使用VPN
- 检查防火墙规则是否允许HTTPS出站连接

### 3. 性能优化建议

如果仍然遇到超时问题，可以考虑：

1. **使用更快的模型**：
   - Qwen-VL-Plus → Qwen-VL-Max（更快但可能精度略低）
   - 或使用本地部署的轻量级模型

2. **异步处理**：
   - 将报告生成改为异步任务
   - 前端轮询获取生成结果
   - 使用WebSocket推送完成通知

3. **缓存相似病例检索结果**：
   - 减少重复的向量检索调用
   - 缓存常见病例的检索结果

4. **图像预处理**：
   - 压缩图像大小（保持诊断质量）
   - 使用更高效的图像格式

## 测试验证

### 1. 测试超时配置

```bash
# 后端日志应显示：
# Qwen-VL API 调用开始，预计耗时较长，请耐心等待...
# Qwen-VL API 调用完成，耗时: 45000ms
```

### 2. 测试错误处理

模拟超时场景：
- 临时将超时设置为5秒
- 触发报告生成
- 应显示友好的超时提示

### 3. 正常流程测试

1. 上传胸部X光影像
2. 点击"生成AI报告"
3. 等待加载（可能需要30-120秒）
4. 成功生成报告

## 故障排查

### 问题1：仍然超时

**检查**:
1. API密钥是否有效
2. 网络连接是否正常
3. API服务是否可用（访问官网检查）
4. 查看后端日志中的详细错误信息

### 问题2：504 Gateway Timeout

**原因**: Nginx或其他反向代理的超时设置过短

**解决**: 增加Nginx的proxy_read_timeout：

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_read_timeout 180s;  # 增加到180秒
    proxy_connect_timeout 10s;
}
```

### 问题3：前端显示"网络错误"

**检查**:
1. 浏览器控制台的Network标签
2. 查看具体的HTTP状态码
3. 检查CORS配置

## 监控建议

建议添加以下监控指标：

1. **AI API调用耗时**：
   - 平均响应时间
   - P95、P99响应时间
   - 超时率

2. **成功率**：
   - 报告生成成功率
   - API调用失败率

3. **用户体验**：
   - 用户等待时间
   - 重试次数

## 更新日志

- 2024-XX-XX: 初始版本，修复超时问题
- 增加RestTemplate超时配置（120秒）
- 增加前端Axios超时配置（150秒）
- 优化错误提示和日志记录
