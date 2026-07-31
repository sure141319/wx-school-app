# 校园闲置集市 (Campus Trade)

面向安徽工业大学学生的校园闲置信息公告栏，包含微信小程序端、Spring Boot 后端，以及维持最低运营能力的审核与公告管理后台。

## 产品边界

- 平台负责闲置信息的发布、结构化展示、搜索与筛选，核心目标是让用户更快找到校内闲置物品。
- 所有人均可浏览商品；卖家填写的 QQ 会公开给包括未登录访客在内的所有访问者，双方在平台外自行联系。
- 平台不提供站内聊天、订单、支付、担保、物流、退款或纠纷调解，也不按多学校平台设计。
- 长期产品事实、技术决策边界和运营优先级以 [`AGENTS.md`](AGENTS.md) 为准。

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
├── scripts/verify.ps1      # 后端与小程序统一质量门禁
└── AGENTS.md               # 产品事实与技术决策边界
```

## 统一质量门禁

在仓库根目录执行：

```powershell
powershell -NoProfile -File .\scripts\verify.ps1
```

该命令会依次运行后端 Maven 测试、小程序 TypeScript 严格检查、回归脚本和 CSS 生成物漂移检查。GitHub Actions 会在每次 push 和 pull request 时执行同一套门禁。

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

使用微信开发者工具打开 `wxui_v2/` 目录。原生小程序 TypeScript 由微信开发者工具编译；首次拉取代码后安装本地质量检查与样式构建依赖：

```bash
cd wxui_v2
npm ci
npm run verify       # 类型检查、回归脚本和 CSS 漂移检查
npm run build:css    # 一次性重新生成 app.wxss
npm run dev:css      # 开发时持续监听并生成 app.wxss
```

小程序测试分层和发布前人工冒烟步骤见 [`wxui_v2/TESTING.md`](wxui_v2/TESTING.md)。

环境切换：编辑 `config/env.ts`，修改 `ENV.current` 为 `'dev'` 或 `'prod'`。

### 管理后台 (checkui/)

```bash
cd checkui
python -m http.server 5173     # 启动静态文件服务
```

或使用任意静态文件服务器在 5173 端口提供 `checkui/` 目录。

## 文档索引

- [`AGENTS.md`](AGENTS.md)：长期产品事实、技术决策边界和运营优先级的唯一来源。
- [`v1/API_DOCUMENTATION.md`](v1/API_DOCUMENTATION.md)：稳定 API 契约、OpenAPI 入口和接口变更规则。
- [`wxui_v2/TESTING.md`](wxui_v2/TESTING.md)：小程序自动化测试边界和发布前人工冒烟检查。
- [`v1/HEIF_DEPLOYMENT.md`](v1/HEIF_DEPLOYMENT.md)：HEIC/HEIF 解码依赖、上线验证和回滚方式。

## 线上环境

- 项目地址：`https://www.ahut-campus.site`
- Swagger UI：`https://www.ahut-campus.site/api/v1/docs`
- OpenAPI JSON：`https://www.ahut-campus.site/api/v1/openapi.json`
