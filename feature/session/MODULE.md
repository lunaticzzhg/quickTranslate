# feature:session 模块说明

## 1. 模块职责

`feature:session` 承载学习详情页相关能力，包括播放器联动、字幕列表、循环播放等核心学习交互。

## 2. 当前包含内容

- `SessionRoute`、`SessionScreen`
- `SessionState` / `SessionIntent` / `SessionEffect`
- `SessionViewModel`
- `SessionNav`（路由参数协议）
- `di/SessionModule.kt`

## 3. 对外暴露能力

- 学习详情页路由入口 `SessionRoute`
- 依赖注入模块 `sessionModule`

## 4. 依赖关系

- 依赖 Compose、Lifecycle Compose、Koin Compose

## 5. 维护注意事项

- 本模块内页面遵循 MVI 架构组织
