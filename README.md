# 胸部X光智能辅助诊断系统

## 项目简介

本系统是一个基于 Spring Boot + Vue 3 + openGauss + MinIO 的胸部X光智能辅助诊断系统，提供病例管理、影像管理、报告生成等核心功能。

## 技术栈

### 后端
- Spring Boot 3.2.0
- MyBatis-Plus 3.5.5
- openGauss 5.0.0
- MinIO 8.5.7
- Redis 7
- JWT

### 前端
- Vue 3.4.0
- Vite 5.0.0
- Element Plus 2.5.0
- Pinia 2.1.7
- Axios 1.6.2

## 快速开始

### 开发环境

1. 启动基础设施（openGauss、MinIO、Redis）：
```bash
docker-compose -f docker-compose.dev.yml up -d
```

2. 启动后端：
```bash
cd backend
mvn spring-boot:run
```

3. 启动前端：
```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:3000

### 生产环境

1. 配置环境变量：
```bash
cp .env.example .env
# 编辑 .env 文件，修改敏感信息
```

2. 启动所有服务：
```bash
docker-compose up -d
```

访问 http://localhost

## 项目结构

```
.
├── backend/                 # Spring Boot 后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/hospital/xray/
│   │   │   │       ├── config/          # 配置类
│   │   │   │       ├── security/        # 安全相关
│   │   │   │       └── common/          # 通用类
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       └── application-prod.yml
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── api/            # API 接口
│   │   ├── components/     # 组件
│   │   ├── views/          # 页面
│   │   ├── stores/         # Pinia 状态管理
│   │   ├── router/         # 路由配置
│   │   ├── utils/          # 工具函数
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   ├── vite.config.js
│   ├── nginx.conf
│   └── Dockerfile
├── docker-compose.yml       # 生产环境配置
├── docker-compose.dev.yml   # 开发环境配置
├── .env.example
└── README.md
```

## 服务端口

- 前端：http://localhost:80 (生产) / http://localhost:3000 (开发)
- 后端：http://localhost:8080
- **Knife4j接口文档**：http://localhost:8080/doc.html
- **Swagger UI**：http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**：http://localhost:8080/v3/api-docs
- openGauss：localhost:5432
- MinIO API：http://localhost:9000
- MinIO Console：http://localhost:9001
- Redis：localhost:6379

## 开发指南

### 后端开发

1. 配置 IDE 的 Maven 和 JDK 17
2. 导入项目
3. 运行 `ChestXrayApplication.java`

### 前端开发

1. 安装依赖：`npm install`
2. 启动开发服务器：`npm run dev`
3. 构建生产版本：`npm run build`

## 数据库初始化

数据库表结构将在后续任务中创建。

## 许可证

Copyright © 2024
