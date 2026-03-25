# QuickTranslate 项目介绍

## 1. 文档目的

本文件用于记录 QuickTranslate 项目的整体说明，并作为各模块介绍文档的统一索引入口。

后续每当项目新增模块、修改模块、删除模块或调整模块结构时，需要同步更新本文件。

## 2. 项目简介

QuickTranslate 是一个面向英语学习场景的 Android 音视频精听应用。

项目目标是支持用户导入本地音频或视频，利用本地模型生成带时间戳的字幕，并在播放过程中实现字幕同步高亮、句子跳转和片段循环播放，用于精听与跟读训练。

## 3. 当前项目文档

当前项目内的规划与规范文档如下：

- 产品需求文档：`/devplan/MVP_PRD.md`
- 开发任务拆解：`/devplan/MVP_DEV_TASKS.md`
- 开发规范：`/devplan/DEVELOPMENT_RULES.md`

## 4. 模块文档索引

当前项目模块与模块说明文档如下：

- `app`：`/app/MODULE.md`
- `core:common`：`/core/common/MODULE.md`
- `domain:project`：`/domain/project/MODULE.md`
- `data:project`：`/data/project/MODULE.md`
- `feature:home`：`/feature/home/MODULE.md`
- `feature:session`：`/feature/session/MODULE.md`
- `feature:transcription`：`/feature/transcription/MODULE.md`

说明：

- 后续每新增一个模块，必须在模块根目录新增 `MODULE.md`
- 并同步将其路径登记到本索引中

## 5. 文档维护规则

- 新增模块时：
  - 在模块根目录创建 `MODULE.md`
  - 在本文件补充模块名称与文档路径
- 修改模块时：
  - 若模块职责、结构、依赖、对外接口发生变化，需同步更新对应 `MODULE.md`
- 删除或重命名模块时：
  - 同步更新本文件中的模块索引信息
