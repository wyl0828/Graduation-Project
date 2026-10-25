# 社区居家养老服务系统

## 从这里开始

- [现行工作文档](./docs/current/README.md)：需求、总体设计、跨模块设计、代码架构、专项和计划。日常修改请改这里。
- [文档使用规则](./docs/README.md)：现行文档、历史资料与运行证据的边界。
- [D1 验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)：本机与干净检出的实际命令、结果及适用边界。
- [D1 初始化记录](./docs/validation/D1工程基座初始化记录_2026-10-25.md)：最初的工程骨架记录。

## 当前进度

D1 工程基座已按[验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)完成：隔离演示库、Flyway 基座迁移、后端健康检查、两套前端构建及干净检出复现均已验证。业务表、业务种子、文件元数据关联、微信开发者工具与真机运行尚不属于这次已验证范围；业务模块未实现，AT/A 运行结果仍为“未测”。[项目计划](./docs/current/项目计划.md)和[工程基座方案](./docs/current/工程基座.md)是当前工作入口。

2026-10-24 的 SRS v1.0、总体设计 v1.1 和 E-01—E-07 集成设计 v1.1 保存在[原始资料快照](./docs/archive/2026-10-25-before-live-docs/README.md)中。其“冻结”描述当时的评审状态；当前设计和实现允许修订。业务范围、权限或关键规则的变化记入[变更记录](./docs/current/变更记录.md)。

## 本地工程

```powershell
./ops/windows/check-dev-env.ps1
./ops/windows/init-local.ps1
# 在本机 MySQL 创建 elderly_care_demo 和 elderly_app，再配置被忽略的 .env.local
./ops/windows/check-demo-db.ps1
./ops/windows/start-api.ps1
# 首次启动完成 Flyway 迁移后
./ops/windows/check-demo-db.ps1 -RequireMigration
```

两个前端分别在 `apps/elder-family/`、`apps/admin-web/` 执行 `npm ci`；前者用 `npm run build:mp-weixin`，后者用 `npm run build`。准确的本机建库步骤、完整命令及验收范围见[工程基座方案](./docs/current/工程基座.md)。脚本不会创建或清空数据库；`.env.local`、`.local-data/`、构建产物均被 Git 忽略。微信小程序 AppID 已配置，编译结果不代表开发者工具或真机验证。
