# 摘要与 Abstract

## 摘要

随着我国人口老龄化进程的不断加快以及“9073”居家养老格局的深化，如何依托数字化手段解决空巢与失能老人的日常看护断层，已成为基层社会治理的核心议题。然而，现有智慧养老系统普遍存在“重静态数据展示、轻线下履约闭环”、“适老化改造流于形式、缺乏认知理论支撑”以及“多方协同下数据权限边界模糊”等突出问题。针对上述痛点，本文以国家无障碍环境建设法规和认知负荷理论为指导，设计并实现了一套基于 Spring Boot 与适老化设计的社区居家养老服务系统。

系统打破了传统系统的单向管理模式，构建了贯通“老年人（受助端）、监护子女（家属端）、网格员与护工（履约端）、社区管理员（调度端）”的四方协同闭环架构。系统核心特色在于研发了**基于网格化与有限状态机（FSM）的社区服务工单流转与调度机制**：将计划性助老服务预约与突发 SOS 紧急求助统一抽象为工单模型，形式化定义了包含 8 种离散状态的有限状态机与角色转移约束矩阵，杜绝了非法状态跳跃；利用数据库行级乐观锁（Optimistic Locking）版本控制技术，在单体分层架构下高效化解了多人并发派单与抢单的竞态冲突；结合现场照片哈希校验，构筑了不可伪造的线下履约证据链。在人机交互方面，系统立足于削减老年人的“外在认知负荷”，在 Uni-app 跨端框架下落地了符合工信部规范的大字号、大触控热区、单向任务闭环及防误触一键求助。此外，系统依托 Spring Boot 3 实现了基于物理社区属地的行级多租户隔离与亲情关系动态授权，并利用 Flyway 实现了工程化数据库平滑增量治理。

在独立隔离的软硬件测试环境下，系统执行了功能性、权限穿透防御、并发防竞态及高并发负载压力测试。实测结果表明：系统 170 余项自动化功能与权限测试全部通过；在 50 并发线程负载冲击下，核心体征上报与工单拉取接口的平均响应时延控制在 $135\text{ms}$ 以内，系统吞吐量超过 $360\text{ req/s}$，错误率为 0.00%。本系统具备优异的业务完整性、高并发可靠性与适老化人机友好性，为基层社区居家养老数字化建设提供了一套高质量、低成本的工程实现方案。

**关键词**：社区居家养老；Spring Boot；适老化设计；有限状态机；工单调度；认知负荷；数据隔离

---

## Abstract

With the rapid acceleration of population aging and the deepening of the "9073" home-based elderly care pattern in China, utilizing digital technologies to resolve the care gap for empty-nest and disabled elderly people has become a critical topic in grassroots social governance. However, existing smart elderly care platforms suffer from notable pain points: overemphasizing static data presentation while lacking offline service fulfillment loops, superficial age-friendly adaptations lacking cognitive theory support, and ambiguous data authority boundaries among multiple stakeholders. To address these issues, guided by national accessibility regulations and Cognitive Load Theory, this paper designs and implements a community home-based elderly care service system based on Spring Boot and age-friendly principles.

Breaking away from traditional unidirectional management models, the system constructs a four-party closed-loop architecture connecting the elderly (beneficiary terminal), monitoring children (family terminal), grid workers/caregivers (fulfillment terminal), and community administrators (dispatching terminal). The core innovation of the system lies in the development of a **community service work order circulation and scheduling mechanism based on grid governance and Finite State Machines (FSM)**. Scheduled service reservations and emergency SOS alerts are abstracted into a unified work order model. An FSM comprising 8 discrete states and strict role-based transition constraint matrices is formally defined, completely eliminating illegal state transitions. Database row-level optimistic locking version control is utilized to resolve race conditions in concurrent dispatching and grabbing without distributed lock complexity. In addition, an unforgeable offline fulfillment chain of evidence is established through on-site photographic hash verification. In terms of human-computer interaction, aiming to minimize the "extraneous cognitive load" of older adults, the Uni-app cross-platform framework delivers large typography ($\ge 18\text{pt}$), large touch targets ($\ge 48\times 48\text{dp}$), unidirectional task loops, and anti-mistouch SOS emergency calls compliant with MIIT specifications. Furthermore, row-level multi-tenant isolation anchored to physical community boundaries and dynamic family authorization revocation are implemented within a Spring Boot 3 modular monolithic architecture, alongside automated database schema evolution managed by Flyway.

Rigorous automated validation, penetration testing, concurrency race tests, and high-concurrency stress tests were conducted in isolated environments. The empirical results demonstrate that over 170 automated test cases passed completely. Under a load of 50 concurrent virtual users, the average response time for core vital reporting and work order pulling interfaces remained within $135\text{ms}$, with a throughput exceeding $360\text{ req/s}$ and an error rate of 0.00%. The system exhibits excellent business completeness, high concurrency reliability, and superior age-friendly accessibility, presenting a high-quality, cost-effective engineering solution for grassroots community elderly care digitalization.

**Keywords**: Community Home-Based Elderly Care; Spring Boot; Age-Friendly Design; Finite State Machine; Work Order Scheduling; Cognitive Load; Data Isolation
