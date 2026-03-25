# data:project 模块说明

## 1. 模块职责

`data:project` 负责学习项目的数据实现层（本地存储、数据映射与仓库实现）。

## 2. 当前包含内容

- `ProjectRepositoryPlaceholder` 占位实现

## 3. 对外暴露能力

- 后续将暴露 `domain:project` 仓库接口的实现

## 4. 依赖关系

- 依赖 `domain:project`

## 5. 维护注意事项

- 数据访问实现保持在本模块，不向 feature 泄露存储细节
