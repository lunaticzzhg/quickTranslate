# data:project 模块说明

## 1. 模块职责

`data:project` 负责学习项目的数据实现层（本地存储、数据映射与仓库实现）。

## 2. 当前包含内容

- `RoomProjectRepository`（当前主实现）
- `local/ProjectEntity`、`ProjectDao`、`QuickTranslateDatabase`
- `di/ProjectDataModule.kt`

## 3. 对外暴露能力

- 已提供 `domain:project` 的 `ProjectRepository` 实现

## 4. 依赖关系

- 依赖 `domain:project`

## 5. 维护注意事项

- 当前默认使用 Room，本地表结构调整需同步评估迁移策略
- 数据访问实现保持在本模块，不向 feature 泄露存储细节
