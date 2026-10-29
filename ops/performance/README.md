# 性能固定数据与负载工具 v1

本目录为 Task 6 Step 5 提供可复跑的本机工具链。它准备独立的合成数据集，并对 API 查询、看板以及可选的体征写入接口发出闭环请求。工具只生成原始请求记录和汇总，不会自动判定 AT-40 通过。

## 固定数据集

fixed_dataset_v1.sql 为版本化 fixture，面向已完成 Flyway 迁移、表结构为空的本机 elderly_care_perf 数据库。目标规模为：

| 数据类型 | 固定数量 |
| --- | ---: |
| 社区 | 2 |
| 老人档案 | 100 |
| 体征记录 | 5,000 |
| 工单 | 500 |
| 预约 | 500 |
| 工单与预约合计 | 1,000 |

数据由纯合成字段组成，老人档案不含身份证、联系电话或真实紧急联系人。固定 ID 1 至 100 用于档案与请求样本。工具还建立一个 COMMUNITY_ADMIN 合成测试账号，凭据与仓库现有种子说明保持一致；负载程序不内置或输出明文凭据。

load_fixed_dataset.ps1 仅接受本机数据库、固定库名 elderly_care_perf 和数据库账号 elderly_app；每个被写入的数据表都必须为空。默认调用只做迁移和空表预检，只有增加 -Apply 才会装载数据。导入后会核对每个固定表的行数。它不会清理、不覆盖、不重置数据库。导入失败或需要复跑时，请废弃并重建这个专用隔离库，再重新迁移和装载。

## 环境准备

需要本机 MySQL 8、PATH 中的 mysql 客户端、可运行项目要求 Java 17 的 PowerShell 环境，以及 Python 3.10 或更新版本。Python 工具只使用标准库，不新增项目依赖。

先由数据库管理员在本机创建空库 elderly_care_perf，并授予 elderly_app 对该库所需的应用连接、Flyway 迁移、临时表和数据写入权限。加载器要求 flyway_schema_history 中存在成功的 V3.10 迁移记录。不要将现有演示库或生产库改名、清空或复用为性能库。

仓库中的 ops/windows/start-api.ps1 为避免误连，固定拒绝除 elderly_care_demo 外的数据库。性能库需通过当前 PowerShell 进程的 DB_* 环境变量直接启动 API。首次启动会运行 Flyway；停止 API 后再装载 fixture，完成后重新启动 API。以下命令只用于本机隔离库：

    $env:DB_HOST = '127.0.0.1'
    $env:DB_PORT = '3306'
    $env:DB_NAME = 'elderly_care_perf'
    $env:DB_USERNAME = 'elderly_app'
    $secure = Read-Host -AsSecureString -Prompt '本机数据库密码'
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
    .\mvnw.cmd -f services\api\pom.xml '-Dspring-boot.run.profiles=local' spring-boot:run

数据库密码只放在当前进程环境变量中。不要把它写入仓库文件或命令参数。API 运行和完成 Flyway 后，按 Ctrl+C 停止；装载完成后用相同 DB_* 环境变量重新启动。

## 装载数据

在专用 PowerShell 中设置加载器环境变量：

    $env:PERF_DB_HOST = '127.0.0.1'
    $env:PERF_DB_PORT = '3306'
    $env:PERF_DB_NAME = 'elderly_care_perf'
    $env:PERF_DB_USERNAME = 'elderly_app'
    $secure = Read-Host -AsSecureString -Prompt '本机数据库密码'
    $env:PERF_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password

预检不会写入数据：

    .\ops\performance\load_fixed_dataset.ps1

确认环境、数据库名、主机和账号后再装载：

    .\ops\performance\load_fixed_dataset.ps1 -Apply

数据库密码不通过命令行参数传递，脚本也不会打印密码。mysql 客户端必须在 PATH 中。

## 运行负载

认证接口为 POST /api/v1/auth/login，使用 WEB 会话 Cookie 与 CSRF 头。测试账号电话和密码只从 PERF_LOGIN_PHONE、PERF_LOGIN_PASSWORD 环境变量读取；登录请求会在写入 CSV 前遮蔽凭据。响应正文不会持久化；CSV 只保留响应字节数、SHA-256、应用 code 和集合项数。Cookie、CSRF 令牌、密码、电话、身份证以及业务响应正文都不会写入结果。

默认目标为 http://127.0.0.1:8080。默认配置是 10 个闭环 worker、连续 300 秒，只运行核心查询和看板接口。负载前设置账号凭据：

    $env:PERF_LOGIN_PHONE = Read-Host -Prompt '隔离性能库管理员账号'
    $secure = Read-Host -AsSecureString -Prompt '隔离性能库管理员密码'
    $env:PERF_LOGIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
    $env:PERF_BASE_URL = 'http://127.0.0.1:8080'
    python .\ops\performance\run_load.py --execute

如需采集核心写操作，在固定数据集目标社区选择一份合成老人档案并显式开启写入：

    python .\ops\performance\run_load.py --include-writes --write-elder-id 1 --execute

写入请求的 recordedAt 固定按 Asia/Shanghai（UTC+08:00）递增，并用无偏移格式传给 LocalDateTime。每个请求同时设置唯一的 Idempotency-Key 请求头与 DTO 的 idempotencyKey 字段：服务端按请求头执行请求级幂等，DTO 字段仅随体征记录持久化，单独依赖 DTO 字段不会防止重复提交。每个新请求会新增一条体征记录，并为新请求头键写入持久化幂等记录；工具不会回滚这些记录。完整复跑前必须重建性能隔离库并重装固定 fixture。固定 fixture 的管理员属于社区 1，推荐写入老人 ID 1 至 50；如单独改变授权范围，必须选择同一社区内的合成老人。

默认只允许 loopback 地址。指向其他非生产隔离测试环境时需显式传入 --allow-nonloopback，并设置 PERF_BASE_URL；禁止将生产地址用作负载目标。本次工作不运行线上或 ECS 压测。

负载组合为档案分页、体征趋势、最新体征、工单派发池、社区看板汇总、看板图表与告警列表。写入场景仅在显式启用时加入。10 个 worker 共用一个已认证管理员会话，因此它表示 10 个并发客户端，不代表 10 个不同身份账号。若验收要求 10 个不同用户，需另行扩展多账号测试方案。

## 输出与口径

每次运行输出到 ops/performance/results/run-<运行编号>/：

- raw_requests.csv：逐请求阶段、worker、接口、状态码、应用码、耗时、错误类别、脱敏请求 JSON、响应字节数、SHA-256 与安全集合项数。不会持久化业务响应正文或响应头；超时和非成功响应均保留。
- summary.json：运行环境、声明的数据规模、并发数、持续时间、请求数、吞吐量、错误率及分类 P50/P95。

P50/P95 使用 nearest-rank 口径，包含全部完成的请求尝试（包括失败请求）。吞吐量以请求总数除以实际测量墙钟时间计算。未返回正常 HTTP 2xx 且应用 code 非 OK 的请求计入错误；本工具不发起预期的业务拒绝请求。

报告记录了 NFR-04/AT-40 的目标：核心查询 P95 不高于 2 秒、核心写操作不高于 3 秒、看板查询不高于 3 秒、非预期请求失败率不高于 1%。管理后台提交成功到页面可见更新的 5 秒目标不由 API 负载工具测量。报告把数据规模记为声明值，负载程序不连接数据库校验行数；请保留加载器的行数核对输出。

runner 的 CPU、内存、操作系统和磁盘由工具收集。服务端 CPU、内存、磁盘、OS、Java、Spring Boot、MySQL、前端、部署和网络信息可通过 PERF_SERVER_OS、PERF_SERVER_CPU、PERF_SERVER_MEMORY、PERF_SERVER_DISK、PERF_SERVER_JAVA、PERF_SERVER_SPRING_BOOT、PERF_SERVER_MYSQL、PERF_SERVER_FRONTEND、PERF_SERVER_DEPLOYMENT、PERF_SERVER_NETWORK 环境变量补齐；未设置时报告“未提供”。

本报告是工具运行记录，不自动判定性能目标达标，也不替代前端可见更新验收。脚本语法检查、SQL 结构静态检查或合成数据报告均不是真实性能结论。运行生成的 CSV/JSON 是本地输出，不应在检查脱敏和必要性前提交到 Git。

## 实现接口依据

工具按当前源码接口构造请求：

- POST /api/v1/auth/login：WEB 会话登录；凭据环境变量不写入报告。
- GET /api/v1/elder/archives?page=1&size=20：档案分页。
- GET /api/v1/health/vitals/trend 与 /latest：体征查询。
- GET /api/v1/work-orders/dispatch-pool：管理员工单查询。
- GET /api/v1/dashboard/community/summary、/charts、/alerts-feed：管理员看板查询。
- 可选 POST /api/v1/health/vitals：社区管理员录入合成体征。

仓库现有种子加载器仅允许向空的 elderly_care_demo 首次装载；该工具另建 elderly_care_perf 固定数据，不对原演示库执行重播种或清理。
