# Campus Trade API 契约说明

本文档只维护稳定的接口约定与变更规则。完整接口、参数、响应 Schema 和校验约束以运行中后端生成的 OpenAPI 为唯一清单，避免手写接口列表与代码长期漂移。

## 契约入口

后端默认监听 `http://localhost:8080`：

- Swagger UI：`http://localhost:8080/api/v1/docs`
- OpenAPI JSON：`http://localhost:8080/api/v1/openapi.json`

接口统一使用 `/api/v1` 前缀。生产环境请替换域名，不要修改路径约定。

## 产品与权限边界

- 商品浏览、分类查询、当前公告和图片代理允许匿名访问。
- 商品详情中的卖家 QQ 属于公开联系方式，未登录访客也可以查看和复制。
- 发布、编辑、上下架、删除、个人资料和上传操作需要登录。
- `/api/v1/audit/**` 用于最低限度的内容治理，只向具备审核权限的账号开放。
- 平台只提供闲置信息展示、搜索与筛选，不提供聊天、订单、支付、担保、物流、退款或纠纷处理接口。

具体授权规则以 `SecurityConfig` 和各业务服务中的资源归属校验共同为准。

## 统一响应

普通业务响应使用 `ApiResponse<T>`：

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {}
}
```

- `success`：请求是否成功。
- `code`：稳定的机器可读业务码；客户端分支判断优先使用它。
- `message`：面向用户或排障的中文说明，不作为程序分支的唯一依据。
- `data`：业务数据；无返回数据时可以为 `null`。

分页数据统一使用 `PageResponse<T>`：

```json
{
  "items": [],
  "total": 0,
  "page": 0,
  "size": 10
}
```

`page` 从 `0` 开始。客户端应根据 `items`、`total`、`page` 和 `size` 判断是否继续加载。

## 认证

需要认证的接口使用 JWT Bearer Token：

```http
Authorization: Bearer <token>
```

认证失败、权限不足、参数校验失败和业务冲突仍返回 `ApiResponse` 结构，并通过 HTTP 状态码与 `code` 表达错误类型。

## 商品详情稳定契约

`GET /api/v1/goods/{id}` 固定返回 `ApiResponse<GoodsDetailResponseDTO>`。匿名访客、普通登录用户、商品所有者和审核人员使用同一个 DTO，不再根据访问者身份切换响应类型。

`GoodsDetailResponseDTO` 的稳定字段为：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 商品 ID |
| `title` / `description` | string | 标题与描述 |
| `price` | number | 价格 |
| `conditionLevel` | string | 成色 |
| `campusLocation` | string | 校内地点 |
| `status` | string | 商品状态 |
| `category` | object/null | 分类公开信息；未归类时为 `null` |
| `seller` | object | 卖家公开信息，包含 `id`、昵称、头像、微信号和 QQ |
| `imageUrls` | string[] | 可展示的图片代理地址 |
| `imageKeys` | string[] | 所有者或审核人员用于编辑的对象键；其他访问者固定为空数组 |
| `auditRemark` | string/null | 所有者或审核人员可见的审核备注；其他访问者为 `null` |
| `createdAt` / `updatedAt` | string | 北京时间业务时间 |

公开响应不得包含卖家邮箱、微信 OpenID、密码摘要或其他内部身份字段。前端不得再从邮箱推导 QQ。

## 接口分组

OpenAPI 中按以下业务前缀组织接口：

- `/api/v1/auth`：注册、登录、验证码和账号认证。
- `/api/v1/goods`：商品搜索、详情、发布与个人商品管理。
- `/api/v1/categories`：分类查询。
- `/api/v1/users`：个人资料及账号绑定。
- `/api/v1/uploads`、`/api/v1/images`：图片上传与公开代理。
- `/api/v1/announcements`：当前公告。
- `/api/v1/audit`：审核与最低限度的运营管理。

具体方法、请求体和当前可用端点只从 OpenAPI 查询，不在本文重复维护。

## 时间契约

- 所有业务时间、用户可见时间和自然日边界统一使用 `Asia/Shanghai`。
- 接口中的 `LocalDateTime` 表示北京时间墙上时间，不带时区偏移。
- 客户端不得把该值当成 UTC，也不得固定加 8 小时修补环境配置错误。
- JWT 有效期、缓存 TTL 和限流窗口等持续时长可以使用时间戳或 UTC 内部计算。

## 契约变更规则

修改接口时按以下顺序维护：

1. 在控制器方法、请求 DTO 和响应 DTO 中表达真实类型及校验约束。
2. 保持 `ApiResponse<T>` 与 `PageResponse<T>` 外层结构稳定。
3. 同步更新小程序 TypeScript 类型和调用代码。
4. 更新本文件中的稳定约定；不要复制 OpenAPI 已能生成的逐接口清单。
5. 运行 `OpenApiDocumentationTest` 和完整后端测试，确认 `/api/v1/openapi.json` 可以生成且关键 Schema 未漂移。

破坏性变更应优先通过新增字段、小步迁移和兼容期完成；生产系统修改必须保留可回滚路径。
