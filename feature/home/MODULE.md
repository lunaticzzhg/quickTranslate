# feature:home 模块说明

## 1. 模块职责

`feature:home` 负责首页相关 UI 与交互逻辑，包括首页状态管理、意图处理和首页路由入口。

## 2. 当前包含内容

- `HomeRoute`、`HomeScreen`
- `HomeState` / `HomeIntent` / `HomeEffect`
- `HomeViewModel`
- `di/HomeModule.kt`（Koin 注入定义）
- 系统文件选择器接入（音频/视频）
- 导入后创建项目记录并展示最近项目列表

## 3. 对外暴露能力

- 首页路由入口 `HomeRoute`（导入媒体后通过回调触发导航到学习详情页）
- 首页依赖注入模块 `homeModule`

## 4. 依赖关系

- 依赖 Compose、Lifecycle Compose、Koin Compose

## 5. 维护注意事项

- 严格按 MVI 组织首页状态与事件
- 业务能力下沉到 domain/data 后，避免在 ViewModel 堆叠实现
