# domain:project 模块说明

## 1. 模块职责

`domain:project` 定义“学习项目”领域模型与后续 UseCase / Repository 接口。

## 2. 当前包含内容

- `Project` 领域实体
- `ProjectSubtitle` 字幕片段领域实体
- `ProjectLoopConfig` 循环配置领域实体
- `CreateProjectInput` 输入模型
- `SubtitleStatus` 字幕状态枚举
- `ProjectRepository` 仓库接口
- `ProjectSubtitleRepository` 字幕仓库接口
- `ProjectLoopConfigRepository` 循环配置仓库接口
- `CreateProjectUseCase`
- `ObserveRecentProjectsUseCase`
- `DeleteProjectUseCase`
- `UpdateProjectSubtitleStatusUseCase`
- `GetProjectSubtitlesUseCase`
- `ReplaceProjectSubtitlesUseCase`
- `GetProjectLoopConfigUseCase`
- `SaveProjectLoopConfigUseCase`
- `di/ProjectDomainModule.kt`

## 3. 对外暴露能力

- 对外暴露项目领域模型、仓库契约与用例

## 4. 依赖关系

- 当前无外部依赖

## 5. 维护注意事项

- 不在本模块放置 Android 相关实现
- 仅保留业务语义与接口契约
