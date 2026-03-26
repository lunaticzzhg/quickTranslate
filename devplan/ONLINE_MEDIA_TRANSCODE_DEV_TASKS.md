# 在线链接转码入口 - 开发任务拆解（V1 + V2：B 站/抖音）

## 1. 文档目标

本文件基于 `ONLINE_MEDIA_TRANSCODE_PRD.md` 输出可执行的开发任务拆解，覆盖：

- V1：直链下载 + 本地媒体 + 转写字幕
- V2：第三方平台解析（站点白名单：B 站、抖音）

原则：任务按最小可交付闭环拆分，逐个完成、逐个编译验证、逐个验收。

## 2. 里程碑规划

### L1（V1）：直链导入闭环

目标：输入 `http/https` 直链 -> 下载 -> 本地媒体 -> 转写 -> 学习。

### L2（V2 基础）：平台解析框架落地

目标：在不绑定具体站点的前提下，建立“平台链接解析”契约、失败分类、白名单与选择 UI。

### L3（V2-1）：B 站端到端

目标：支持 `www.bilibili.com` / `m.bilibili.com` / `b23.tv` 页面链接解析出可下载条目，并跑通全流程。

### L4（V2-2）：抖音端到端

目标：支持 `www.douyin.com` / `v.douyin.com` / `www.iesdouyin.com` 页面链接解析出可下载条目，并跑通全流程。

### L5：稳定性收口

目标：完善错误提示、日志、重试与清理策略，覆盖常见失败场景，避免卡死。

## 3. 任务拆解（按执行顺序）

### T1：新增“链接导入”UI 与路由

- 范围：
  - 首页新增“导入链接”入口
  - 新增链接导入页（输入/粘贴/校验）
  - 支持系统分享 intent（粘贴 URL 后进入导入页）
- 交付物：
  - 用户可输入 `http/https` URL 并触发“创建项目 + 入队”
- 验收：
  - 能从首页与分享入口进入该页；URL 校验失败有明确提示

### T2：定义“链接导入”用例（创建项目 + 入队）

- 范围（domain）：
  - 新增 `CreateProjectFromUrlUseCase`（命名可调整）
    - 输入：`sourceUrl`
    - 输出：`projectId`
  - 规则：
    - 先创建项目记录（displayName 可先用 URL 或待解析 title）
    - 入队转码任务（V1 先使用单任务类型，如 `DOWNLOAD_AND_TRANSCRIBE`）
- 验收：
  - 创建项目后可在首页出现；队列可看到对应任务

### T3：V1 下载阶段（OkHttp）+ 本地文件落地

- 范围（data/app/feature）：
  - 新增下载器（OkHttp），支持：
    - 进度回调（bytes/total -> progress）
    - 可取消（项目删除/用户取消时）
  - 统一项目目录规划：按 `projectId` 分目录，文件名稳定
  - 任务执行器中加入“下载阶段”，成功后拿到本地 `filePath`
- 验收：
  - 直链（音频/视频）能下载到本地，且删除项目会清理文件

### T4：V1 转写对接（复用现有 pipeline）

- 范围：
  - 下载完成后，将“本地媒体路径”作为转写输入（避免远程 URL 直转写）
  - 复用现有 `SessionTranscriptionPipeline` 执行与字幕落库
- 验收：
  - 断网情况下仍能打开项目并播放（至少音频）+ 加载字幕

### T5：任务状态扩展（下载/解析/转写）

- 范围：
  - 扩展任务表/状态模型（至少支持：
    - `queued`
    - `resolving`（V2）
    - `downloading`
    - `transcribing`
    - `succeeded/failed`
  - 转码看板与学习页展示对应状态与进度
- 验收：
  - 下载阶段进度可见，失败原因可见，重试可用

### T6：V2 基础 - 平台解析契约与失败分类

- 范围（domain）：
  - 定义 `PlatformLinkResolver` 契约：
    - 输入：`url` + 可选 `headers/cookies`
    - 输出：`ResolvedMedia`（title/duration/items[]）
  - 定义失败类型枚举（`UNSUPPORTED_SITE/LOGIN_REQUIRED/REGION_RESTRICTED/DRM_PROTECTED/EXTRACT_FAILED`）
  - 定义站点白名单配置（B 站、抖音 host）
- 验收：
  - 非直链但命中白名单时进入 `resolving` 流程；不在白名单明确提示

### T7：V2 基础 - 解析选择 UI（多条目/多清晰度）

- 范围（feature）：
  - 解析成功后展示可下载条目列表（至少支持“默认条目”单选）
  - 用户确认后才入队下载（避免盲下大文件）
- 验收：
  - 解析结果展示正确；用户可选择后再开始下载

### T8：V2-1 B 站 - 短链展开与解析实现（白名单站点 1）

- 范围（data）：
  - 支持 `b23.tv` 重定向展开到最终 `www.bilibili.com`/`m.bilibili.com`
  - 实现 `BilibiliPlatformLinkResolver`
  - 解析策略约束：
    - 仅对公开可访问内容尽力解析
    - 若需要登录：允许用户在 WebView 登录以获取 Cookie（不做绕过）
- 验收：
  - 输入 B 站公开视频链接，可解析出至少 1 个可下载条目，并完成下载 + 转写 + 学习闭环

### T9：V2-1 B 站 - 登录态（可选）与 Cookie 注入链路

- 范围（feature/data）：
  - 提供“在 WebView 打开并登录（可选）”入口
  - 解析/下载阶段可携带 Cookie/Referer/User-Agent（按 resolver 输出）
- 验收：
  - 需要登录的内容不会静默失败：能提示 `LOGIN_REQUIRED`，并允许用户走登录尝试

### T10：V2-2 抖音 - 短链展开与解析实现（白名单站点 2）

- 范围（data）：
  - 支持 `v.douyin.com` 重定向展开到最终 `www.douyin.com`
  - 实现 `DouyinPlatformLinkResolver`
  - 失败分类与提示优先完善（站点结构变化频繁）
- 验收：
  - 输入抖音公开视频链接，可解析并完成下载 + 转写 + 学习闭环；解析失败能给出可操作提示

### T11：稳定性收口（两站共用）

- 范围：
  - 失败可重试：解析失败/下载失败/转写失败分别可重试
  - 删除项目联动取消任务并清理产物（含下载文件、转写临时文件、字幕数据）
  - 进程重启恢复：按现有队列 `restoreAndSignal()` 语义恢复
  - 日志：关键节点（resolve/download/transcribe）输出可诊断日志（避免泄露 Cookie）
- 验收：
  - 覆盖 `ONLINE_MEDIA_TRANSCODE_PRD.md` 的 V1/V2 验收标准，不出现“RUNNING 永久卡死”

