# player:core 模块说明

## 1. 模块职责

`player:core` 提供播放器封装能力，负责基于 Media3 管理媒体加载、播放控制和播放状态分发。

## 2. 当前包含内容

- `SessionPlayer` 播放器抽象接口
- `ExoSessionPlayer` Media3 实现
- `PlaybackState` 播放状态模型
- `di/PlayerModule.kt`

## 3. 对外暴露能力

- 向业务模块提供可注入的播放器实例和统一控制接口

## 4. 依赖关系

- 依赖 Media3 ExoPlayer
- 依赖 Koin Android

## 5. 维护注意事项

- 播放状态更新节奏需兼顾实时性与性能
- 页面销毁时必须显式释放播放器资源
