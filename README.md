# 校园闲置集市 (Campus Trade)

安徽工业大学校园二手交易平台 —— 专为校内学生设计的闲置物品买卖平台，包含微信小程序端、Spring Boot 后端、审核与公告管理后台。

## 技术栈

| 层 | 技术 |
|---|------|
| **小程序前端** | 微信原生小程序 (WXML / WXSS / TS) |
| **后端** | Spring Boot 3.3.5 / Java 17 / MyBatis 3.0.4 |
| **数据库** | MySQL (生产) / H2 (测试) |
| **缓存** | Redis + Spring Cache |
| **对象存储** | MinIO |
| **认证** | JWT (jjwt 0.12.6) + BCrypt |
| **数据库迁移** | Flyway |
| **管理后台** | 原生 HTML / CSS / JS (Vanilla) |
| **邮件** | Spring Mail (QQ SMTP) |

## 项目结构

```
campus_app/
├── v1/                    # Spring Boot 后端
├── wxui_v2/               # 微信小程序前端
├── checkui/               # 图片审核、商品与公告管理后台
└── CLAUDE.md              # AI 辅助开发指南
```

## 快速开始

### 后端 (v1/)

```bash
cd v1
mvn clean package                              # 构建 JAR
mvn spring-boot:run                            # 启动开发服务器 (端口 8080)
mvn test                                       # 运行所有测试 (H2 内存数据库)
mvn test -Dtest=ClassName                      # 运行单个测试类
java -jar target/backend-0.0.1-SNAPSHOT.jar   # 运行打包好的 JAR
```

需要配置以下环境变量 (见 `application.yml`):

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接信息 | — |
| `JWT_SECRET` | JWT 签名密钥 (至少 32 字符) | — |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 | localhost:6379 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | QQ 邮箱 SMTP 配置 | — |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | MinIO 配置 | — |
| `MINIO_PUBLIC_BASE_URL` | 可公开访问的对象存储或 CDN 基础地址，配置后小程序图片 URL 直接走该地址 | — |

生产环境的 `DB_URL` 必须显式要求 Connector/J 把 MySQL 会话固定为 UTC+8，例如：

```text
jdbc:mysql://localhost:3306/campus_trade?useSSL=false&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true&useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&allowPublicKeyRetrieval=true
```

### 小程序 (wxui_v2/)

使用微信开发者工具打开 `wxui_v2/` 目录。无需构建步骤，原生小程序项目直接运行。

环境切换：编辑 `config/env.ts`，修改 `ENV.current` 为 `'dev'` 或 `'prod'`。

### 管理后台 (checkui/)

```bash
cd checkui
python -m http.server 5173     # 启动静态文件服务
```

或使用任意静态文件服务器在 5173 端口提供 `checkui/` 目录。

## 功能概览

### 微信小程序 (5 个页面)

| 页面 | 路径 | 功能 |
|---|---|---|
| 首页 | `pages/index/index` | 商品瀑布流浏览、分类筛选、关键词搜索、无限滚动 |
| 登录 | `pages/auth/auth` | 邮箱注册/登录/密码重置 (限 QQ 邮箱)，发送验证码 |
| 商品详情 | `pages/goods/detail` | 图片轮播、价格/成色/校区信息、卖家 QQ 一键复制 |
| 发布商品 | `pages/publish/publish` | 发布/编辑商品、多图上传、分类选择 |
| 个人中心 | `pages/profile/profile` | 个人资料编辑、我发布的商品管理 (编辑/上下架/删除) |

**交互特点：**
- 买卖双方通过卖家 QQ 号联系 (卖家注册邮箱即 QQ 邮箱)
- 新发布或修改的商品自动进入审核状态 (`PENDING_REVIEW`)
- 审核中的商品图片在前端显示为占位图

### 后端 API (34 个接口)

所有接口均返回统一格式 `ApiResponse<T>` (`{ success, message, data }`)，分页接口内嵌 `PageResponse` (`{ items, total, page, size }`)。

**认证** (`/api/v1/auth/*`) —— 公开接口
- `POST /auth/email-code` — 发送验证码
- `POST /auth/register` — 注册
- `POST /auth/login` — 登录
- `POST /auth/reset-password` — 重置密码
- `GET /auth/me` — 当前用户信息 (需认证)

**商品** (`/api/v1/goods/*`)
- `GET /goods` — 商品列表 (支持 keyword/categoryId/status 筛选，分页 + 缓存)
- `GET /goods/{id}` — 商品详情
- `POST /goods` — 创建商品 (需认证)
- `PUT /goods/{id}` — 更新商品 (需认证)
- `DELETE /goods/{id}` — 删除商品 (需认证)
- `PATCH /goods/{id}/status` — 上下架 (需认证)
- `GET /goods/mine` — 我的商品 (需认证)

**分类** (`/api/v1/categories`) —— 公开接口
- `GET /categories` — 获取所有启用的分类

**用户** (`/api/v1/users/*`) —— 需认证
- `GET /users/me` — 获取个人资料
- `PUT /users/me` — 更新个人资料 (头像修改触发审核)

**文件上传** (`/api/v1/uploads/*`) —— 需认证
- `POST /uploads/image` — 上传图片

**图片审核** (`/api/v1/audit/images/*`) —— 需审核权限
- `GET /audit/images` — 商品图片审核队列
- `POST /audit/images/{id}/approve` — 通过图片
- `POST /audit/images/{id}/reject` — 驳回图片
- `POST /audit/images/approve-all-rejected` — 批量通过全部已驳回图片（需确认标识）
- `POST /audit/images/reject-all-approved` — 批量驳回全部已通过图片（需确认标识）
- `GET /audit/images/avatars` — 头像审核队列
- `POST /audit/images/avatars/{userId}/approve` — 通过头像
- `POST /audit/images/avatars/{userId}/reject` — 驳回头像

**图片代理** (`/api/v1/images/**`) —— 公开接口
- `GET /images/{year}/{month}/{filename}` — MinIO 图片代理，带 7 天浏览器缓存

**公告**
- `GET /announcements/current` — 获取当前启用公告（公开）
- `GET /audit/announcement` — 获取公告配置（需审核权限）
- `PUT /audit/announcement` — 更新公告配置并递增版本（需审核权限）

### 管理后台 (3 个页面)

| 页面 | 文件 | 功能 |
|---|---|---|
| 图片审核台 | `checkui/html/index.html` | 商品图片/用户头像的审核队列，支持通过/驳回操作 |
| 商品管理 | `checkui/html/goods.html` | 商品列表管理，支持搜索筛选、单/批量删除 |
| 公告管理 | `checkui/html/announcement.html` | 编辑公告标题、正文和启用状态，内容变化时自动递增版本 |

三个页面共享同一套认证 Session (localStorage)，登录状态跨页面互通。

## 核心数据库表结构 (6 张表)

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `users` | 用户 | email (唯一), password_hash, nickname, avatar_url, 登录失败锁定 |
| `category_do` | 商品分类 | name, sort_order, enabled |
| `goods_do` | 商品 | seller_id, category_id, title, price, condition_level, campus_location, status |
| `goods_image_do` | 商品图片 | goods_id, image_url, sort_order, audit_status (审核状态) |
| `upload_object_do` | 上传对象生命周期 | user_id, object_key, status, bound_type, expires_at |
| `announcement_config` | 单条公告配置 | title, content, enabled, revision, updated_at |

**商品状态流转：** `PENDING_REVIEW` → (审核通过) → `ON_SALE` ⇄ `OFF_SHELF`，审核驳回 → `REJECTED`

**图片审核状态：** 每张商品图片和头像均有独立审核状态 `PENDING` / `APPROVED` / `REJECTED`，商品全部图片通过后才上架。

## 安全设计

- **无状态 JWT 认证**：每次请求携带 `Authorization: Bearer <token>`，24 小时过期
- **密码加密**：BCrypt 哈希存储
- **登录保护**：连续失败 5 次锁定 15 分钟
- **验证码限制**：60 秒冷却、每小时 6 次发送上限，单个验证码最多错误 5 次
- **邮箱限制**：仅允许 QQ 邮箱 (`@qq.com`)
- **审核权限**：仅配置的审核员用户 ID 可访问审核接口
- **XSS 防护**：管理后台对渲染内容进行 HTML 转义
- **文件上传安全**：限制 10MB、仅允许 jpg/jpeg/png/webp/heic/heif 格式，并校验 MIME 与文件头

## 部署

生产环境地址: `https://www.ahut-campus.site`

- **后端**: `https://www.ahut-campus.site/api/v1`
- **管理后台**: 静态文件部署，连接同一 API
- **图片存储**: MinIO (生产 bucket: `campus-trade`)；生产建议配置 `MINIO_PUBLIC_BASE_URL` 指向对象存储公开域名或 CDN，减少后端图片代理流量
- **数据库**: MySQL (Flyway 自动迁移)
- **小程序**: 微信审核发布

## 项目决策记录

- **暂不新增业务索引 / 搜索索引**：当前应用预期用户量约 500-1000，商品和审核队列规模较小，现阶段不为商品列表、审核列表或关键字搜索新增额外索引。后续如果出现明显慢查询、数据量增长或运营侧批量管理压力，再通过 Flyway 迁移补充复合索引或全文索引。
