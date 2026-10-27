# 演示种子数据

本目录包含仅供本机开发演示使用的虚构/合成数据：

- `demo_synthetic_seed_m01_m02.sql`
- `demo_synthetic_seed_m03_m12.sql`

首次装载请使用 `ops/windows/load-demo-seed.ps1`。加载器会先校验目标为 `elderly_care_demo`，并要求目标业务表为空；两个 SQL 文件是首次装载用的完整种子，不是重置脚本，也不能用于覆盖或重播种已使用的演示库。

加载器及 SQL 保护不能替代备份。需要重建数据时，应使用新建的隔离数据库和合成数据，不要清空或重放正在使用的演示库。
