# 后端 API

Spring Boot 单体分层应用的工程入口。当前仅包含启动、健康检查、Flyway 迁移入口与 MyBatis-Plus 基础依赖，不实现业务模块。

从仓库根目录运行：

```powershell
./ops/windows/init-local.ps1
# 编辑 .env.local，配置本机隔离演示数据库账号
./ops/windows/start-api.ps1
```

健康检查地址：`http://127.0.0.1:8080/actuator/health`。应用启动时自动执行版本化迁移，并创建配置的本地文件目录。
