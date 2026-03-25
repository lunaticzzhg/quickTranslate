# feature:transcription 模块说明

## 1. 模块职责

`feature:transcription` 负责字幕生成流程相关能力，包括转写触发、进度状态管理与失败重试。

## 2. 当前包含内容

- `TranscriptionStatus` 状态定义（idle/queued/processing/success/failed）
- `TranscriptionSegment` 转写片段模型
- `MockTranscriptionService` mock 转写实现（用于驱动 UI 联调）

## 3. 对外暴露能力

- 暴露 mock 转写服务与状态模型，供学习页接入字幕生成流程

## 4. 依赖关系

- 依赖 Kotlin Coroutines（模拟异步转写耗时）

## 5. 维护注意事项

- 状态模型保持稳定、可序列化，便于后续持久化与恢复
