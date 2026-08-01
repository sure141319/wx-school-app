---
name: campus-trade-miniprogram
description: Campus Trade（campus_app）仓库的微信原生小程序专属工作流。修改、审查、调试、测试、预览或发布 wxui_v2 时使用；先读取根目录 AGENTS.md，保持现有 WebView、Spring Boot HTTP API、北京时间和公开联系方式契约，并运行 npm run verify。不得用于其他仓库、纯后端任务或 checkui 管理后台任务。
---

# Campus Trade 微信小程序

## 开始工作

1. 完整读取仓库根目录 `AGENTS.md`，以其中的产品事实和决策边界为准。
2. 检查 Git 工作区，保留用户已有改动。
3. 读取需求涉及的页面、组件、公共工具、配置和测试，不套用通用小程序模板。
4. 将修改限制在 `wxui_v2/`；只有用户同时要求时才修改 `v1/` 后端。

## 保持现有实现

- 保持 WebView，不擅自启用 Skyline 或 Worklet。
- 保持 Spring Boot HTTP API，不引入 CloudBase 或 `wx.cloud`。
- 保持现有标准图标 `tabBar`、严格模式 TypeScript、TDesign 版本和按页面注册方式。
- 优先复用现有 `utils/`、`services/`、组件和设计 token，不复制同类逻辑。
- 将 `styles/global.css` 视为全局样式源；修改后运行 `npm run build:css` 同步 `app.wxss`。
- 接口字段、认证、上传和状态码必须核对现有前端封装及后端契约。
- 时间和公开/登录访问边界严格遵守 `AGENTS.md`，不得自行推断或扩大登录限制。

## 修改与验证

1. 选择满足需求的最小文件集合，做小而可回滚的改动。
2. 覆盖相关的成功、失败、空态、重复操作和未登录路径。
3. 在 `wxui_v2/` 运行 `npm run verify`。
4. 涉及 WXML、WXSS、组件生命周期或 `wx.*` API 时，用微信开发者工具验证。
5. 涉及剪贴板、上传、授权、广告、键盘、安全区或网络时，补充真机验证。
6. 无法完成某项验证时，明确说明未验证内容和原因。

## 发布边界

- 只有用户明确要求时才执行预览、上传、审核、发布或回退，并区分报告每个动作。
- 不把代码上传密钥、AppSecret、token 或生产配置写入源码、日志和提交记录。
- 发布前按 `wxui_v2/TESTING.md` 完成关键路径人工冒烟；失败时停止发布。
