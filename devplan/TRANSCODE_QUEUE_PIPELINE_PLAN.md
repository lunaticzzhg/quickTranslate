# 转码任务队列设计方案（串行 + 动态优先级 + 后台执行）

## 1. 目标与约束

### 1.1 目标

- 转码任务必须串行执行（同一时刻仅 1 个运行中任务）
- 任务支持优先级与动态重排
- 用户进入学习页时，对应项目任务若未完成，应提升到队列首位
- 不抢占当前执行中的任务（仅影响待执行任务顺序）
- 用户频繁切换学习页时，队列顺序可持续调整
- 任务在后台持续执行（退出学习页后仍继续）

### 1.2 非目标

- 本期不做并行转码
- 本期不做跨设备同步
- 本期不做“已运行任务强制中断并切换”

---

## 2. 总体架构（Pipeline + Queue）

在现有转写 pipeline 基础上，新增“队列调度层”，形成两层：

1. **Queue 层（调度）**
- 负责排队、重排、领取下一个任务、状态流转
- 保证串行与非抢占

2. **Pipeline 层（执行）**
- 负责单个任务的实际执行
- 已有阶段：媒体准备 -> 转写执行 -> 字幕持久化

建议结构：

- `domain/project`：任务队列仓库接口 + usecase
- `data/project`：Room 实体/DAO/Repository 实现
- `feature/session`：页面触发入队与优先级提升
- `app`：应用级队列引擎启动器（Application Scope）

---

## 3. 数据模型（Room）

新增表：`project_transcode_task`

字段建议：

- `id: Long`（PK）
- `projectId: Long`（索引）
- `mediaUri: String`
- `taskType: String`（先固定 `TRANSCRIBE`，为后续留扩展）
- `status: String`（`PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED`）
- `basePriority: Int`（默认 0）
- `boostSeq: Long`（动态优先级序号，越大越靠前）
- `retryCount: Int`
- `errorMessage: String?`
- `createdAt: Long`
- `updatedAt: Long`
- `startedAt: Long?`
- `finishedAt: Long?`

约束建议：

- 活跃任务唯一性：同一 `projectId + taskType` 仅允许一个活跃任务（`PENDING/RUNNING`）
- 通过 upsert 避免重复入队

排序规则（只对 `PENDING` 生效）：

1. `boostSeq DESC`
2. `basePriority DESC`
3. `createdAt ASC`

说明：

- 用户每次进入学习页触发 `boostSeq = nextSeq()`，即可实现“最近关注项目靠前”
- 不影响 `RUNNING` 任务，天然非抢占

---

## 4. 核心流程设计

### 4.1 入队（导入媒体或需转码时）

`enqueueOrRefresh(projectId, mediaUri)`：

- 若存在该项目活跃任务：
  - 更新 `mediaUri/updatedAt`（可选）
  - 若状态为 `PENDING` 可按需刷新 `boostSeq`
- 若不存在：
  - 新建 `PENDING` 任务

然后触发 `ensureQueueEngineRunning()`。

### 4.2 页面进入时提升优先级

`bumpTaskPriority(projectId)`：

- 若该项目存在 `PENDING` 任务：更新其 `boostSeq = nextSeq()`
- 若该项目为 `RUNNING`：不做处理（已在执行）
- 若不存在任务或已完成：不做处理

行为符合“挪到第一位，但不抢占当前任务”。

### 4.3 调度与串行执行（生产者-消费者）

生产者（多来源）：

- 导入流程入队 `enqueueOrRefresh`
- 学习页进入触发 `bumpTaskPriority`
- 可选：失败重试入口入队

消费者（唯一协程）：

- `TranscodeQueueEngine` 在应用级 `CoroutineScope` 中启动单个消费协程
- 内部使用 `Mutex` + `isDraining` 防抖，确保同一时刻只有一个 drain 循环
- 通过 `Channel<Unit>(CONFLATED)` 或 `MutableSharedFlow(replay=0, extraBufferCapacity=1)` 接收“队列变更信号”

消费循环：

1. 原子领取下一个任务（事务）：
  - 查询队首 `PENDING`
  - CAS 更新为 `RUNNING`
2. 调用现有 `SessionTranscriptionPipeline.run(...)`
3. 回写 `SUCCEEDED/FAILED`
4. 继续领取下一个，直到无任务

串行保障：

- 单消费者协程（同进程）
- 领取任务使用数据库事务，防止并发 Worker 重复领取

---

## 5. 后台执行方案

采用 **纯协程生产者-消费者模型（不使用 WorkManager）**：

- 在 `Application` 启动时创建 `AppScope` 与 `TranscodeQueueEngine`
- Engine 生命周期独立于学习页，不依赖页面生命周期
- 学习页退出后任务继续，符合需求

进程存活边界说明：

- 该方案保证“页面退出后继续执行”，但**不保证进程被系统杀死后自动恢复**
- 进程重启后可在 `Application#onCreate` 主动执行 `engine.signal()`，继续消费数据库中 `PENDING` 任务
- 若后续需要“系统级强保活与重启恢复”，再升级到前台服务或 WorkManager

### 5.1 首页加载后恢复机制（必选）

在**首页首屏数据加载完成后**执行队列恢复（而非 `Application#onCreate` 立即执行）：

1. `Application` 仅初始化 `TranscodeQueueEngine`（不触发恢复）
2. `Home` 页在“加载完成”事件（如 `HomeLoaded`）中调用 `engine.restoreAndSignal()`
3. `restoreAndSignal()` 行为：
  - 将异常残留的 `RUNNING` 任务重置为 `PENDING`
  - 保留原有 `boostSeq/basePriority/createdAt`，不破坏排序语义
  - 发送一次消费信号，触发 drain 循环

幂等要求：

- `restoreAndSignal()` 可重复调用且结果一致
- 若当前已经在 drain，不应启动第二个消费者
- 首页可能多次进入，恢复入口必须幂等

---

## 6. 关键接口草案

### 6.1 Domain

- `EnqueueTranscodeTaskUseCase`
- `BumpTranscodeTaskPriorityUseCase`
- `ObserveTranscodeQueueUseCase`
- `ObserveProjectTranscodeTaskUseCase`

### 6.2 Repository

- `enqueueOrRefresh(...)`
- `bumpPriority(projectId)`
- `claimNextPendingTask(now): Task?`（事务）
- `markRunning(id)`
- `markSuccess(id)`
- `markFailed(id, error)`
- `hasPendingOrRunning(): Boolean`
- `listPendingOrdered(): List<Task>`

---

## 7. 与现有页面交互点

学习页（进入时）：

- 调用 `BumpTranscodeTaskPriorityUseCase(projectId)`
- 若该项目字幕未完成，展示“排队中/执行中”状态

导入流程：

- 创建项目后调用 `EnqueueTranscodeTaskUseCase`
- 不直接在页面内阻塞执行

---

## 8. 异常与边界处理

- **重复点击多个页面**：仅更新 `boostSeq`，不会产生重复任务
- **当前任务失败**：标记 `FAILED`，继续执行后续任务
- **应用进程被杀**：当前协程停止；下次进入首页且首页加载完成后，触发 `restoreAndSignal()` 继续 drain 队列
  - 恢复时先执行 `RUNNING -> PENDING`，再触发 drain
- **任务执行中项目被删除**：
  - 方案A：删除时取消活跃任务并清理产物
  - 方案B：允许执行完后写入失败并清理（推荐 A，更干净）
- **同项目媒体变更**：`enqueueOrRefresh` 更新任务输入并重置为 `PENDING`（若未运行）

---

## 9. 实施步骤（建议按任务逐个落地）

1. 新增 Room 实体/DAO/Migration（任务表）
2. 新增 domain repository 接口与 usecase
3. data 层实现队列仓库（含事务领取）
4. 新增 `TranscodeQueueEngine`（单消费者协程 + 信号驱动 drain）
5. 学习页接入 `bumpPriority`
6. 导入流程接入 `enqueue`
7. 首页/学习页增加队列状态展示
8. 联调与压力测试（频繁切页重排、后台持续执行）

---

## 10. 验收标准

- 同时入队多个任务时，任意时刻只运行 1 个任务
- 进入学习页可使对应 `PENDING` 任务在下一轮成为首个待执行任务
- 当前运行任务不被抢占
- 退出学习页后队列继续推进
- 高频切换学习页时队列顺序可持续变化且无重复任务

---

## 11. 兼容现有代码的最小改动策略

- 保留现有 `SessionTranscriptionPipeline` 作为“单任务执行器”
- 新增队列调度层，不改动已有转写阶段实现
- 页面只从“直接触发转写”切换为“入队 + 观察状态”

这样可以降低回归风险，并逐步把转写流程迁移到统一队列框架。
