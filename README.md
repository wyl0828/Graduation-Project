# 社区居家养老服务系统

## 从这里开始

- [项目文档导航](./docs/README.md)：现行规范、业务决策、验证记录与历史快照分别存放。
- [现行工作文档](./docs/current/README.md)：需求、总体设计、跨模块设计、代码架构和计划。
- [项目实施计划](./docs/current/项目计划.md)：当前推进顺序与完成门槛。
- [本地运行指南](./docs/current/运行指南.md)：建库、配置、启动 API 和两个前端。
- [D1 验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)：本机与干净检出的实际命令、结果及适用边界。

## 当前进度

> **2026-10-26 本机共享工作区验证**：最新隔离临时 MySQL 复验的 HTTP/隔离/注销断言 **42 项通过**；同一临时库 Maven 全套 **137 项（133 通过、0 失败、4 项按环境跳过）**。真实 HTTP 覆盖服药打卡、预约履约、SOS 和注销后的旧 token 失效；浏览器验收增加合成档案真实写入/服务端读回、看板空态/错误态及 H5 退出切换。临时服务与数据目录已停止并清理。详见[系统隔离联调记录](./docs/validation/系统隔离联调记录_2026-10-26.md)及[演示闭环验收记录](./docs/validation/演示闭环验收记录_2026-10-26.md)。结论对应当前未提交工作区，不代表确定提交、干净检出、线上或真机结果。

1. **工程基座**：历史 D1 验收见[原记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)。本轮隔离实例实测 Flyway V1–V3.3、合成种子、API 启动和跨角色 HTTP 主链。
2. **后端**：数据库会话与角色授权、当前社区档案分页/状态写入边界和看板空分母语义已修复；最新隔离数据库上的 Maven 全套为 137 项。该测试数不是功能完成率，也不覆盖所有并发、恢复和安全边界。
3. **前端**：管理端构建、oxlint、ESLint、小程序类型检查、H5 与微信小程序构建通过。浏览器已用隔离 API 验证四角色页面、合成档案写入/服务端读回、注销和看板错误/空态；微信开发者工具及真机页面仍未测。
4. **待完成**：无短信服务，手机号找回、子女注册及老人首次激活安全关闭；跨社区/跨对象权限矩阵、业务并发与幂等、文件证据资产校验、审计完整性及使用中演示库的数据库/文件联合备份恢复仍需按[修复计划](./docs/superpowers/plans/2026-10-25-全量审查问题修复计划.md)继续验证与完善。看板首次接单响应时长尚未按 Q09-04/AT-37 实现，近 30 天/年度趋势后端也未实现，当前页面只展示近 7 天。不能据本轮主链通过宣称全模块交付。

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
