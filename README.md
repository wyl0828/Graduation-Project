# 社区居家养老服务系统

## 从这里开始

- [项目文档导航](./docs/README.md)：现行规范、业务决策、验证记录与历史快照分别存放。
- [现行工作文档](./docs/current/README.md)：需求、总体设计、跨模块设计、代码架构和计划。
- [项目实施计划](./docs/current/项目计划.md)：当前推进顺序与完成门槛。
- [本地运行指南](./docs/current/运行指南.md)：建库、配置、启动 API 和两个前端。
- [D1 验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)：本机与干净检出的实际命令、结果及适用边界。

## 当前进度

> **2026-10-26 本机共享工作区验证**：在独立临时 MySQL 实例上完成 Flyway、合成种子、后端全套测试 **136 项（132 通过、0 失败、4 项按环境跳过）**，以及真实 HTTP 的服药打卡、子女预约→派单→接单→履约照片→审核完成、老人 SOS→派工→结案三条链。临时服务与数据目录在验收后已停止并清理。详见[系统隔离联调记录](./docs/validation/系统隔离联调记录_2026-10-26.md)。这份结论对应当前未提交工作区，不代表确定提交、干净检出、线上或真机结果。

1. **工程基座**：历史 D1 验收见[原记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)。本轮隔离实例实测 Flyway V1–V3.3、合成种子、API 启动和跨角色 HTTP 主链。
2. **后端**：数据库会话与角色授权、当前社区档案分页/状态写入边界已修复；本轮隔离数据库上的 Maven 全套为 136 项。该测试数不是功能完成率，也不覆盖所有并发、恢复和安全边界。
3. **前端**：管理端构建、小程序类型检查、H5 与微信小程序构建通过。浏览器已用隔离 API 验证管理员、网格员、老人和子女登录及主要只读数据页；网格员业务写入由 HTTP 脚本验证，浏览器写入/失败分支尚未全量验收。微信开发者工具和真机在本轮未重测。
4. **待完成**：无短信服务，手机号找回、子女注册及老人首次激活安全关闭；亲情关系管理缺少可验证的关系列表与 ID；看板跨社区统计、文件证据资产校验、数据库幂等与审计完整性、使用中演示库的联合备份恢复仍需按[修复计划](./docs/superpowers/plans/2026-10-25-全量审查问题修复计划.md)继续验证与完善。不能据本轮主链通过宣称全模块交付。

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
```

两个前端分别在 `apps/elder-family/`、`apps/admin-web/` 执行 `npm ci`；前者用 `npm run build:mp-weixin`，后者用 `npm run build`。微信开发者工具导入 `apps/elder-family/dist/build/mp-weixin/`。
