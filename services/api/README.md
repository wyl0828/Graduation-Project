# 后端 API

Spring Boot 单体服务，包含 `common/`、`infrastructure/`、`coordination/` 和 `modules/` 业务包。当前代码职责见[后端代码架构](../../docs/current/后端代码架构.md)；旧的“仅有骨架、业务包未创建”描述保存在[整理前快照](../../docs/archive/2026-10-26-before-document-consolidation/后端代码架构.md)。

从仓库根目录按[工程基座](../../docs/current/工程基座.md)配置本地环境。独立临时库完整验证入口：

```powershell
./ops/windows/tests/Test-SystemHttpTempDb.ps1
```

最近一次测试和真实 HTTP 范围见[系统隔离联调记录](../../docs/validation/系统隔离联调记录_2026-10-26.md)。本地 API 启动命令为 `./ops/windows/start-api.ps1`，默认健康检查地址为 `http://127.0.0.1:8080/actuator/health`；实际端口以本地配置为准。
