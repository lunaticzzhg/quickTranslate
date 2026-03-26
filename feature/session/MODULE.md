# feature:session 模块说明

## 1. 模块职责

`feature:session` 承载学习详情页相关能力，包括播放器联动、字幕列表、循环播放等核心学习交互。

## 2. 当前包含内容

- `SessionRoute`、`SessionScreen`
- `SessionState` / `SessionIntent` / `SessionEffect`
- `SessionViewModel`
- `loop/SessionLoopController`（循环状态机拆分）
- `playback/SessionPlaybackCoordinator`（播放位置恢复/持久化拆分）
- `transcription/SessionTranscriptionCoordinator`（转写与字幕恢复拆分）
- `transcription/SessionTranscriptionPipeline`（按阶段执行转写：媒体准备 -> 转写执行 -> 字幕持久化）
- `transcription/SessionMediaPrepareStage`（媒体准备阶段）
- `transcription/SessionRemoteMediaDownloadStage`（远程媒体下载并落地本地文件）
- `transcription/SessionTranscriptionExecuteStage`（转写执行阶段）
- `transcription/SessionSubtitlePersistStage`（字幕持久化阶段）
- `transcription/SessionProjectTranscodeTaskExecutor`（队列任务执行器，桥接到转写 pipeline）
- `SessionNav`（路由参数协议，包含 `projectId`）
- `di/SessionModule.kt`
- Media3 播放控制与播放进度展示
- 字幕模型、时间命中高亮与点击跳转
- 单句循环播放（1/3/5/∞）与手动停止
- 多句连续字幕选区循环
- mock 转写状态接入（queued/processing/success/failed）与失败重试入口
- 支持真实本地转写模式（whisper.cpp CLI）与 mock 模式切换
- 转写状态回写项目记录（用于首页状态联动）
- 学习页重进时优先恢复已落库字幕，避免重复触发 mock 转写
- 学习页通过“转码任务队列”触发转写（入队+提权+状态观察），而非页面内直接长任务执行
- 队列任务执行时，对 `http/https` 媒体先下载到本地项目目录，再执行转写
- 学习页重试转写时优先读取项目最新 `mediaUri` 入队（下载后走本地路径）
- 循环配置持久化（选区与循环次数）并在重进时恢复
- 最近播放位置恢复（进入学习页自动 seek）与退出时保存

## 3. 对外暴露能力

- 学习详情页路由入口 `SessionRoute`
- 依赖注入模块 `sessionModule`

## 4. 依赖关系

- 依赖 Compose、Lifecycle Compose、Koin Compose
- 依赖 `player:core`
- 依赖 `feature:transcription`
- 依赖 `domain:project`

## 5. 维护注意事项

- 本模块内页面遵循 MVI 架构组织
