## 项目概述

校园闲置集市（Campus Trade）— 安徽工业大学学生的二手信息平台。该仓库包含三个组件：

- **`v1/`** — 基于 Spring Boot 3.3.5 / Java 17 的后端（REST API、JWT 认证、MySQL/Redis/MinIO）
- **`wxui_v2/`** — 微信小程序前端
- **`checkui/`** — 用于商品审核的管理控制台（Vanilla HTML/CSS/JS）

## 构建与运行命令

### 后端（v1/）
```bash
cd v1
mvn clean package              # 打包 JAR
mvn spring-boot:run            # 启动开发服务器（端口 8080）
mvn test                       # 运行所有测试（使用 H2 内存数据库，无需外部服务）
mvn test -Dtest=ClassName      # 运行单个测试类
java -jar target/backend-0.0.1-SNAPSHOT.jar  # 运行已打包的 JAR
```

### 前端（wxui_v2/）
在微信开发者工具中打开 `wxui_v2/`。无构建步骤 — 原生小程序。

### 管理界面（checkui/）
```bash
cd checkui
python -m http.server 5173     # 在 5173 端口提供静态文件服务
```

## 架构

### 后端分层结构

`v1/src/main/java/com/campustrade/platform/` 下的每个领域模块遵循：
```
module/
├── controller/    # @RestController — REST 接口
├── service/       # @Service — 业务逻辑
├── mapper/        # @Mapper — MyBatis 接口
├── dataobject/    # 数据库实体类（DO 后缀）
├── dto/request/   # 入参 DTO（Java record）
├── dto/response/  # 出参 DTO（Java record）
├── assembler/     # DO ↔ DTO 转换（手写，不使用 MapStruct）
└── enums/         # 领域枚举
```

**领域模块：** auth、user、goods、category、message、upload、audit、security、config、common。

### 后端关键模式

- **统一响应：** 所有接口返回 `ApiResponse<T>`（record，包含 `success`、`message`、`data`）
- **分页：** `PageResponse<T>` 用于包装列表结果，包含 `items`、`total`、`page`、`size`
- **异常处理：** `GlobalExceptionHandler` 捕获所有异常并映射为 HTTP 状态码
- **DTO 为 Java record**（不可变）
- **Assembler** 为手动实现的 DO↔DTO 转换器（未使用 MapStruct）
- **缓存：** Spring Cache + Redis（当 Redis 禁用时回退到 ConcurrentMapCacheManager）
- **认证：** 无状态 JWT，通过 `JwtAuthenticationFilter` 在 Spring Security 过滤链之前处理
- **配置属性：** 自定义 `AppProperties` 类位于 `config/` 下，配置前缀为 `app.*`，位于 `application.yml` 中
- **MyBatis XML 映射文件** 位于 `v1/src/main/resources/mapper/`
- **Flyway 迁移脚本** 位于 `v1/src/main/resources/db/migration/`（基线：`V1__baseline.sql`）
- **API 基础路径：** `/api/v1/*` — 详细接口请参见 `v1/API_DOCUMENTATION.md`

### 数据库

- **生产环境：** MySQL | **测试环境：** H2（MySQL 兼容模式）
- **表：** `users`、`category_do`、`goods_do`、`goods_image_do`、`conversation_do`、`messages`

### 前端（wxui_v2/）

- 

### 测试设置

- 使用 JUnit 5 + Spring Boot Test + Mockito
- 测试使用 H2 内存数据库并对外部服务进行 Mock（开发环境 Redis 禁用，MinIO 被 Mock）
- 测试配置：`v1/src/test/resources/application.yml`
- 测试模式：使用 `@SpringBootTest` 并结合内置 `@Configuration`、`@MockBean`、`@TestPropertySource`

# 校园闲置集市项目 - AI 交互规范
## 语言要求
强制要求：**全程使用中文**进行所有交互
- 代码解释、架构说明、BUG 排查、命令讲解、文档编写 100% 使用中文
- 仅允许保留代码本身的英文关键字、变量名、类名（如 Spring Boot、JWT、MySQL）
- 禁止使用英文句子、英文缩写、英文注释进行沟通

## 项目适配
针对本项目（SpringBoot 后端 + 微信小程序 + 管理控制台），所有技术支持均遵循中文回答规则。