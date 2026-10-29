# 社区居家养老服务系统

## 从这里开始

- [项目文档导航](./docs/README.md)：现行规范、业务决策、验证记录与历史快照分别存放。
- [现行工作文档](./docs/current/README.md)：需求、总体设计、跨模块设计、代码架构和计划。
- [项目实施计划](./docs/current/项目计划.md)：当前推进顺序与完成门槛。
- [本地运行指南](./docs/current/运行指南.md)：建库、配置、启动 API 和两个前端。
- [D1 验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)：本机与干净检出的实际命令、结果及适用边界。

## 当前进度

> **2026-10-29 更新**：ECS release `20261028-087e4a2-live` 已上线，管理端、H5、API 健康入口和抽查静态资源均已验收。完整构建、线上和备份证据见[ECS 生产发布与线上验收记录](./docs/validation/ECS生产发布与线上验收记录_2026-10-29.md)。

项目的交付验收阶段仍未整体完成：数据库/文件恢复、真实回滚演练、异机备份、真机/微信开发者工具、非开发者走查和性能验收仍开放。论文 Word 与写作按用户要求暂缓。

## 本地工程

```powershell
./ops/windows/check-dev-env.ps1
./ops/windows/init-local.ps1
# 在本机 MySQL 创建 elderly_care_demo 和 elderly_app，再配置被忽略的 .env.local
./ops/windows/check-demo-db.ps1
# 已配置的本机演示库测试入口（会加载 .env.local）
./ops/windows/run-test.ps1
./ops/windows/start-api.ps1
# 启动完成 Flyway 迁移后核验
./ops/windows/check-demo-db.ps1 -RequireMigration
# 仅在目标库确认空白、应用写入静默时首次加载合成种子
./ops/windows/load-demo-seed.ps1
# 独立临时 MySQL + Flyway + seed + 全套测试 + 跨角色 HTTP 验收；不触碰当前演示库
./ops/windows/tests/Test-SystemHttpTempDb.ps1
# 工程入口配置/目标拒绝 + 首次种子事务与并发保护；使用并清理独立临时 MySQL
./ops/windows/tests/Test-DemoDbGuard.ps1
```

两个前端分别在 `apps/elder-family/`、`apps/admin-web/` 执行 `npm ci`；前者在被忽略的 `src/manifest.json` 中填写自己的 `mp-weixin.appid` 后，通用构建使用 `npm run build:mp-weixin`，当前 ECS 目标使用 `npm run build:mp-weixin:ecs`，后者用 `npm run build`。`init-local.ps1` 会从无凭据的模板创建该本机文件。微信开发者工具导入 `apps/elder-family/dist/build/mp-weixin/`。
