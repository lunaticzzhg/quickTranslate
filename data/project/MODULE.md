# data:project 模块说明

## 1. 模块职责

`data:project` 负责学习项目的数据实现层（本地存储、数据映射与仓库实现）。

## 2. 当前包含内容

- `RoomProjectRepository`（当前主实现）
- `RoomProjectSubtitleRepository`
- `RoomProjectLoopConfigRepository`
- `RoomProjectPlaybackStateRepository`
- `local/ProjectEntity`、`ProjectDao`、`QuickTranslateDatabase`
- `local/ProjectSubtitleEntity`、`ProjectSubtitleDao`
- `local/ProjectLoopConfigEntity`、`ProjectLoopConfigDao`
- `local/ProjectPlaybackStateEntity`、`ProjectPlaybackStateDao`
- `SubtitleStatusMapper`、`ProjectMapper`、`ProjectSubtitleMapper`、`ProjectLoopConfigMapper`
- `di/ProjectDataModule.kt`
- 支持项目字幕状态回写（`subtitleStatus` + `updatedAtEpochMs`）
- 支持最近播放位置持久化（独立播放状态表）
- 支持按项目持久化字幕列表并替换更新
- 支持按项目持久化循环配置（选区起止 + 次数选项）

## 3. 对外暴露能力

- 已提供 `domain:project` 的 `ProjectRepository` 实现

## 4. 依赖关系

- 依赖 `domain:project`

## 5. 维护注意事项

- 当前默认使用 Room，本地表结构调整需同步评估迁移策略
- 数据访问实现保持在本模块，不向 feature 泄露存储细节
