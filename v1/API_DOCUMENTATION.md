# Campus Trade Platform - API 文档

> 版本: v1 | 基础路径: `/api/v1` | 协议: HTTPS | 数据格式: JSON

---

## 目录

- [通用说明](#通用说明)
- [认证模型](#认证模型)
- [数据字典](#数据字典)
- [认证接口](#1-认证接口-auth)
- [商品接口](#2-商品接口-goods)
- [分类接口](#3-分类接口-categories)
- [用户接口](#4-用户接口-users)
- [消息接口](#5-消息接口-messages)
- [上传接口](#6-上传接口-uploads)
- [图片代理](#7-图片代理-images)
- [图片审核](#8-图片审核-audit)
- [接口总览表](#接口总览表)

---

## 通用说明

### 统一响应格式

所有接口返回统一的 `ApiResponse<T>` 结构：

```json
{
  "success": true,
  "message": "OK",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | `boolean` | 请求是否成功 |
| `message` | `string` | 可读状态信息 |
| `data` | `T` | 实际响应数据，失败时为 `null` |

### 分页响应格式

列表类接口使用 `PageResponse<T>` 分页结构：

```json
{
  "items": [ ... ],
  "total": 100,
  "page": 0,
  "size": 10
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `T[]` | 当前页数据列表 |
| `total` | `long` | 总记录数 |
| `page` | `int` | 当前页码（从 0 开始） |
| `size` | `int` | 每页条数 |

### 错误响应

| HTTP 状态码 | 场景 |
|-------------|------|
| `400` | 参数校验失败、请求格式错误 |
| `401` | 未认证、JWT 无效或过期 |
| `403` | 权限不足 |
| `404` | 资源不存在 |
| `409` | 资源冲突（如邮箱已注册） |
| `429` | 请求过于频繁 |
| `413` | 上传文件超过 10MB 限制 |
| `500` | 服务器内部错误 |

### 技术栈

- **框架**: Spring Boot 3.3.5, Java 17
- **ORM**: MyBatis
- **数据库**: MySQL (Flyway 迁移)
- **缓存**: Redis + Spring Cache
- **对象存储**: MinIO
- **邮件**: SMTP (QQ 邮箱)

---

## 认证模型

### JWT Bearer Token

- **请求头**: `Authorization: Bearer <token>`
- **Token 获取**: 通过登录或注册接口返回
- **有效期**: 默认 1440 分钟（24 小时），可通过 `app.jwtExpirationMinutes` 配置
- **Token 载荷**: 包含 `sub`（userId）和 `email` 声明

### 接口权限分类

| 类型 | 说明 |
|------|------|
| **公开接口** | 无需认证，所有用户可访问 |
| **认证接口** | 需要有效的 JWT Token |

---

## 数据字典

### GoodsStatusEnum（商品状态）

| 值 | 说明 |
|----|------|
| `ON_SALE` | 在售中 |
| `OFF_SHELF` | 已下架 |

### ImageAuditStatusEnum（图片审核状态）

| 值 | 说明 |
|----|------|
| `PENDING` | 待审核 |
| `APPROVED` | 审核通过 |
| `REJECTED` | 审核驳回 |

### VerificationPurposeEnum（验证码用途）

| 值 | 说明 |
|----|------|
| `REGISTER` | 注册验证 |
| `RESET_PASSWORD` | 重置密码验证 |

---

## 1. 认证接口 `/auth`

> 所有认证接口均为**公开接口**，无需 JWT Token。

### 1.1 发送邮箱验证码

```
POST /api/v1/auth/email-code
```

**请求体** (`SendCodeRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `email` | `string` | 是 | 必须为 `@qq.com` 邮箱 |
| `purpose` | `string` | 否 | `REGISTER` 或 `RESET_PASSWORD`，默认 `REGISTER` |

**请求示例**:
```json
{
  "email": "123456@qq.com",
  "purpose": "REGISTER"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "验证码已发送",
  "data": {
    "delivered": true
  }
}
```

**业务规则**:
- 注册场景：邮箱已存在时拒绝发送
- 重置密码场景：邮箱不存在时拒绝发送
- 验证码有效期 5 分钟
- 重发冷却 60 秒
- 每小时限制 8 次请求

---

### 1.2 用户注册

```
POST /api/v1/auth/register
```

**请求体** (`RegisterRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `email` | `string` | 是 | 必须为 `@qq.com` 邮箱 |
| `code` | `string` | 是 | 6 位数字验证码 |
| `password` | `string` | 是 | 6-64 字符 |
| `nickname` | `string` | 是 | 1-64 字符 |

**请求示例**:
```json
{
  "email": "123456@qq.com",
  "code": "654321",
  "password": "mypassword",
  "nickname": "校园用户"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "token": "eyJhbGci...",
    "user": {
      "id": 1,
      "email": "123456@qq.com",
      "nickname": "校园用户",
      "avatarUrl": null
    }
  }
}
```

---

### 1.3 用户登录

```
POST /api/v1/auth/login
```

**请求体** (`LoginRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `email` | `string` | 是 | 必须为 `@qq.com` 邮箱 |
| `password` | `string` | 是 | 6-64 字符 |

**请求示例**:
```json
{
  "email": "123456@qq.com",
  "password": "mypassword"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGci...",
    "user": {
      "id": 1,
      "email": "123456@qq.com",
      "nickname": "校园用户",
      "avatarUrl": "https://..."
    }
  }
}
```

**安全规则**:
- 连续登录失败 5 次后账号锁定 15 分钟

---

### 1.4 重置密码

```
POST /api/v1/auth/reset-password
```

**请求体** (`ResetPasswordRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `email` | `string` | 是 | 必须为 `@qq.com` 邮箱 |
| `code` | `string` | 是 | 6 位数字验证码 |
| `newPassword` | `string` | 是 | 6-64 字符 |

**请求示例**:
```json
{
  "email": "123456@qq.com",
  "code": "654321",
  "newPassword": "newpassword123"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "密码重置成功",
  "data": null
}
```

---

### 1.5 获取当前用户信息

```
GET /api/v1/auth/me
```

**权限**: 需要 JWT Token

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "email": "123456@qq.com",
    "nickname": "校园用户",
    "avatarUrl": "https://..."
  }
}
```

---

## 2. 商品接口 `/goods`

### 2.1 搜索/列表商品（公开）

```
GET /api/v1/goods
```

**查询参数**:

| 参数 | 类型 | 必填 | 校验规则 | 默认值 |
|------|------|------|----------|--------|
| `keyword` | `string` | 否 | 最大 100 字符 | - |
| `categoryId` | `Long` | 否 | - | - |
| `status` | `string` | 否 | `ON_SALE` 或 `OFF_SHELF` | - |
| `page` | `int` | 否 | >= 0 | `0` |
| `size` | `int` | 否 | 1-50 | `10` |

**请求示例**:
```
GET /api/v1/goods?status=ON_SALE&page=0&size=24&keyword=教材&categoryId=1
```

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [
      {
        "id": 1,
        "title": "二手教材",
        "description": "九成新，无笔记",
        "price": 25.50,
        "conditionLevel": "八成新",
        "campusLocation": "图书馆",
        "status": "ON_SALE",
        "category": { "id": 1, "name": "书籍", "sortOrder": 1 },
        "seller": { "id": 2, "email": "...", "nickname": "卖家", "avatarUrl": "..." },
        "imageUrls": ["https://...", "https://..."],
        "createdAt": "2026-04-01T10:00:00",
        "updatedAt": "2026-04-01T10:00:00"
      }
    ],
    "total": 50,
    "page": 0,
    "size": 10
  }
}
```

---

### 2.2 商品详情（公开）

```
GET /api/v1/goods/{id}
```

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `id` | `Long` | 是 |

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "title": "二手教材",
    "description": "九成新，无笔记",
    "price": 25.50,
    "conditionLevel": "八成新",
    "campusLocation": "图书馆",
    "status": "ON_SALE",
    "category": { "id": 1, "name": "书籍", "sortOrder": 1 },
    "seller": { "id": 2, "email": "...", "nickname": "卖家", "avatarUrl": "..." },
    "imageUrls": ["https://...", "https://..."],
    "createdAt": "2026-04-01T10:00:00",
    "updatedAt": "2026-04-01T10:00:00"
  }
}
```

---

### 2.3 发布商品

```
POST /api/v1/goods
```

**权限**: 需要 JWT Token

**请求体** (`GoodsSaveRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `title` | `string` | 是 | 最大 120 字符 |
| `description` | `string` | 是 | 最大 5000 字符 |
| `price` | `BigDecimal` | 是 | >= 0.01 |
| `conditionLevel` | `string` | 是 | 最大 50 字符 |
| `campusLocation` | `string` | 是 | 最大 120 字符 |
| `categoryId` | `Long` | 否 | - |
| `imageUrls` | `string[]` | 是 | 1-9 个元素，每个最大 500 字符 |

**请求示例**:
```json
{
  "title": "二手教材",
  "description": "九成新，无笔记，适合考研",
  "price": 25.50,
  "conditionLevel": "八成新",
  "campusLocation": "图书馆",
  "categoryId": 1,
  "imageUrls": ["https://...", "https://..."]
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "商品发布成功",
  "data": {
    "id": 1,
    "title": "二手教材",
    "description": "九成新，无笔记，适合考研",
    "price": 25.50,
    "conditionLevel": "八成新",
    "campusLocation": "图书馆",
    "status": "ON_SALE",
    "category": { "id": 1, "name": "书籍", "sortOrder": 1 },
    "seller": { "id": 1, "email": "...", "nickname": "校园用户", "avatarUrl": "..." },
    "imageUrls": ["https://...", "https://..."],
    "createdAt": "2026-04-01T10:00:00",
    "updatedAt": "2026-04-01T10:00:00"
  }
}
```

---

### 2.4 更新商品

```
PUT /api/v1/goods/{id}
```

**权限**: 需要 JWT Token（仅商品所有者可操作）

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `id` | `Long` | 是 |

**请求体**: 同 `2.3 发布商品`

**响应示例**:
```json
{
  "success": true,
  "message": "商品更新成功",
  "data": { ... }
}
```

---

### 2.5 删除商品

```
DELETE /api/v1/goods/{id}
```

**权限**: 需要 JWT Token（仅商品所有者可操作）

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `id` | `Long` | 是 |

**响应示例**:
```json
{
  "success": true,
  "message": "商品删除成功",
  "data": null
}
```

---

### 2.6 更新商品状态

```
PATCH /api/v1/goods/{id}/status
```

**权限**: 需要 JWT Token（仅商品所有者可操作）

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `id` | `Long` | 是 |

**请求体** (`GoodsStatusUpdateRequestDTO`):

| 字段 | 类型 | 必填 |
|------|------|------|
| `status` | `string` | 是 |

**请求示例**:
```json
{
  "status": "OFF_SHELF"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "商品状态更新成功",
  "data": { ... }
}
```

---

### 2.7 我的商品

```
GET /api/v1/goods/mine
```

**权限**: 需要 JWT Token

**查询参数**:

| 参数 | 类型 | 必填 | 校验规则 | 默认值 |
|------|------|------|----------|--------|
| `page` | `int` | 否 | >= 0 | `0` |
| `size` | `int` | 否 | 1-50 | `10` |

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [ ... ],
    "total": 5,
    "page": 0,
    "size": 10
  }
}
```

---

## 3. 分类接口 `/categories`

> 所有分类接口均为**公开接口**。

### 3.1 获取全部分类

```
GET /api/v1/categories
```

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": [
    { "id": 1, "name": "书籍", "sortOrder": 1 },
    { "id": 2, "name": "电子产品", "sortOrder": 2 },
    { "id": 3, "name": "生活用品", "sortOrder": 3 }
  ]
}
```

---

## 4. 用户接口 `/users`

> 所有用户接口均**需要 JWT Token**。

### 4.1 获取个人资料

```
GET /api/v1/users/me
```

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "email": "123456@qq.com",
    "nickname": "校园用户",
    "avatarUrl": "https://..."
  }
}
```

---

### 4.2 更新个人资料

```
PUT /api/v1/users/me
```

**请求体** (`UpdateProfileRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `nickname` | `string` | 是 | 最大 64 字符 |
| `avatarUrl` | `string` | 否 | 最大 500 字符 |

**请求示例**:
```json
{
  "nickname": "新昵称",
  "avatarUrl": "https://..."
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "个人资料更新成功",
  "data": {
    "id": 1,
    "email": "123456@qq.com",
    "nickname": "新昵称",
    "avatarUrl": "https://..."
  }
}
```

---

## 5. 消息接口 `/messages`

> 所有消息接口均**需要 JWT Token**。

### 5.1 发起会话

```
POST /api/v1/messages/conversations
```

**请求体** (`StartConversationRequestDTO`):

| 字段 | 类型 | 必填 |
|------|------|------|
| `goodsId` | `Long` | 是 |

**请求示例**:
```json
{
  "goodsId": 5
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "会话已创建",
  "data": {
    "id": 1,
    "goodsId": 5,
    "goodsTitle": "二手教材",
    "goodsCoverImage": "https://...",
    "buyer": { "id": 2, "email": "...", "nickname": "买家", "avatarUrl": "..." },
    "seller": { "id": 1, "email": "...", "nickname": "卖家", "avatarUrl": "..." },
    "lastMessageAt": "2026-04-01T10:00:00"
  }
}
```

---

### 5.2 获取会话列表

```
GET /api/v1/messages/conversations
```

**查询参数**:

| 参数 | 类型 | 必填 | 校验规则 | 默认值 |
|------|------|------|----------|--------|
| `page` | `int` | 否 | >= 0 | `0` |
| `size` | `int` | 否 | 1-50 | `20` |

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [
      {
        "id": 1,
        "goodsId": 5,
        "goodsTitle": "二手教材",
        "goodsCoverImage": "https://...",
        "buyer": { "id": 2, "email": "...", "nickname": "买家", "avatarUrl": "..." },
        "seller": { "id": 1, "email": "...", "nickname": "卖家", "avatarUrl": "..." },
        "lastMessageAt": "2026-04-01T10:00:00"
      }
    ],
    "total": 3,
    "page": 0,
    "size": 20
  }
}
```

---

### 5.3 获取会话消息

```
GET /api/v1/messages/conversations/{conversationId}/messages
```

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `conversationId` | `Long` | 是 |

**查询参数**:

| 参数 | 类型 | 必填 | 校验规则 | 默认值 |
|------|------|------|----------|--------|
| `page` | `int` | 否 | >= 0 | `0` |
| `size` | `int` | 否 | 1-200 | `50` |

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [
      {
        "id": 1,
        "conversationId": 1,
        "sender": { "id": 2, "email": "...", "nickname": "买家", "avatarUrl": "..." },
        "content": "请问还在吗？",
        "createdAt": "2026-04-01T10:00:00"
      }
    ],
    "total": 10,
    "page": 0,
    "size": 50
  }
}
```

---

### 5.4 发送消息

```
POST /api/v1/messages/messages
```

**请求体** (`SendMessageRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `conversationId` | `Long` | 是 | - |
| `content` | `string` | 是 | 最大 1000 字符 |

**请求示例**:
```json
{
  "conversationId": 1,
  "content": "请问还在吗？"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "消息发送成功",
  "data": {
    "id": 2,
    "conversationId": 1,
    "sender": { "id": 1, "email": "...", "nickname": "卖家", "avatarUrl": "..." },
    "content": "请问还在吗？",
    "createdAt": "2026-04-01T10:01:00"
  }
}
```

---

## 6. 上传接口 `/uploads`

### 6.1 上传图片

```
POST /api/v1/uploads/image
```

**权限**: 需要 JWT Token

**Content-Type**: `multipart/form-data`

**表单参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | `MultipartFile` | 是 | 图片文件，最大 10MB |

**响应示例**:
```json
{
  "success": true,
  "message": "Upload success",
  "data": {
    "url": "https://...",
    "filename": "abc123.jpg"
  }
}
```

---

### 6.2 批量生成预签名 URL

```
POST /api/v1/uploads/presign/batch
```

**权限**: 需要 JWT Token

**请求体** (`PresignRequestDTO`):

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `urls` | `string[]` | 是 | 非空数组 |

**请求示例**:
```json
{
  "urls": ["https://minio-host/bucket/images/2026/04/file1.jpg"]
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "https://minio-host/bucket/images/2026/04/file1.jpg": "https://presigned-url?signature=..."
  }
}
```

---

## 7. 图片代理 `/images`

> 图片代理接口为**公开接口**。

### 7.1 获取图片

```
GET /api/v1/images/{year}/{month}/{filename}
```

**路径参数**:

| 参数 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `year` | `string` | 是 | 4 位数字（如 `2026`） |
| `month` | `string` | 是 | 2 位数字（`01`-`12`） |
| `filename` | `string` | 是 | 不包含 `..`、`/`、`\` |

**响应**: 原始图片字节流

**响应头**:
- `Content-Type`: 根据文件类型自动设置
- `Cache-Control`: `max-age=604800, public`（7 天缓存）
- `ETag`: MinIO 对象 ETag

---

## 8. 图片审核 `/audit`

> 所有审核接口均**需要 JWT Token**，且当前用户必须是配置的审核员（`app.imageAudit.reviewerUserIds`）。

### 8.1 获取待审核图片列表

```
GET /api/v1/audit/images
```

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 |
|------|------|------|--------|
| `status` | `string` | 否 | - |
| `page` | `int` | 否 | `0` |
| `size` | `int` | 否 | `10`（最大 50） |

**响应示例**:
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [
      {
        "imageId": 1,
        "goodsId": 5,
        "goodsTitle": "二手教材",
        "sellerId": 2,
        "sellerNickname": "卖家",
        "originalImageUrl": "https://...",
        "sortOrder": 0,
        "auditStatus": "PENDING",
        "auditRemark": null,
        "auditedBy": null,
        "auditedAt": null,
        "createdAt": "2026-04-01T10:00:00"
      }
    ],
    "total": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 8.2 通过图片

```
POST /api/v1/audit/images/{imageId}/approve
```

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `imageId` | `Long` | 是 |

**响应示例**:
```json
{
  "success": true,
  "message": "Image approved",
  "data": {
    "imageId": 1,
    "auditStatus": "APPROVED",
    "auditedBy": 1,
    "auditedAt": "2026-04-01T10:00:00"
  }
}
```

---

### 8.3 驳回图片

```
POST /api/v1/audit/images/{imageId}/reject
```

**路径参数**:

| 参数 | 类型 | 必填 |
|------|------|------|
| `imageId` | `Long` | 是 |

**请求体** (`ImageRejectRequestDTO`，可选）:

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| `remark` | `string` | 否 | 最大 500 字符 |

**请求示例**:
```json
{
  "remark": "图片包含违规内容"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "Image rejected",
  "data": {
    "imageId": 1,
    "auditStatus": "REJECTED",
    "auditRemark": "图片包含违规内容",
    "auditedBy": 1,
    "auditedAt": "2026-04-01T10:00:00"
  }
}
```

---

## 接口总览表

| # | 方法 | 路径 | 认证 | 说明 |
|---|------|------|------|------|
| 1 | `POST` | `/api/v1/auth/email-code` | 公开 | 发送邮箱验证码 |
| 2 | `POST` | `/api/v1/auth/register` | 公开 | 用户注册 |
| 3 | `POST` | `/api/v1/auth/login` | 公开 | 用户登录 |
| 4 | `POST` | `/api/v1/auth/reset-password` | 公开 | 重置密码 |
| 5 | `GET` | `/api/v1/auth/me` | 需要 | 获取当前用户信息 |
| 6 | `GET` | `/api/v1/goods` | 公开 | 搜索/列表商品 |
| 7 | `GET` | `/api/v1/goods/{id}` | 公开 | 商品详情 |
| 8 | `POST` | `/api/v1/goods` | 需要 | 发布商品 |
| 9 | `PUT` | `/api/v1/goods/{id}` | 需要 | 更新商品 |
| 10 | `DELETE` | `/api/v1/goods/{id}` | 需要 | 删除商品 |
| 11 | `PATCH` | `/api/v1/goods/{id}/status` | 需要 | 更新商品状态 |
| 12 | `GET` | `/api/v1/goods/mine` | 需要 | 我的商品列表 |
| 13 | `GET` | `/api/v1/categories` | 公开 | 获取全部分类 |
| 14 | `GET` | `/api/v1/users/me` | 需要 | 获取个人资料 |
| 15 | `PUT` | `/api/v1/users/me` | 需要 | 更新个人资料 |
| 16 | `POST` | `/api/v1/messages/conversations` | 需要 | 发起会话 |
| 17 | `GET` | `/api/v1/messages/conversations` | 需要 | 获取会话列表 |
| 18 | `GET` | `/api/v1/messages/conversations/{id}/messages` | 需要 | 获取会话消息 |
| 19 | `POST` | `/api/v1/messages/messages` | 需要 | 发送消息 |
| 20 | `POST` | `/api/v1/uploads/image` | 需要 | 上传图片 |
| 21 | `POST` | `/api/v1/uploads/presign/batch` | 需要 | 批量生成预签名 URL |
| 22 | `GET` | `/api/v1/images/{year}/{month}/{filename}` | 公开 | 获取图片 |
| 23 | `GET` | `/api/v1/audit/images` | 需要 | 待审核图片列表 |
| 24 | `POST` | `/api/v1/audit/images/{id}/approve` | 需要 | 通过图片 |
| 25 | `POST` | `/api/v1/audit/images/{id}/reject` | 需要 | 驳回图片 |
