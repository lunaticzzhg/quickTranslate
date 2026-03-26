# yt-dlp Android 接入方案（更新）

## 1. 目标

在 QuickTranslate 内提供可稳定运行的 YouTube 下载能力，避免“Linux 预编译二进制在 Android 运行失败（403/Permission denied/No such file）”问题。

当前主方案已切换为：**优先接入 `youtubedl-android`（内置 Python + yt-dlp runtime）**，并保留 `quicktranslate.ytdlp.path` 作为外部可执行兜底。

---

## 2. 现状问题

当前失败根因包括：

- 官方 `yt-dlp_linux_aarch64` 是 Linux glibc 目标，不是 Android 目标
- Android `filesDir` 常见 `noexec`，可执行文件即使存在也无法直接运行
- 不同设备上动态链接器/ABI 兼容性差异导致 `error=2/13` 等错误

---

## 3. 方案原则

1. 产物必须是 `arm64-v8a` Android 可执行目标
2. 运行路径优先 `nativeLibraryDir`（与 whisper 一致）
3. 失败信息必须可观测（可复制的完整错误）
4. 保留外部路径配置作为调试兜底

---

## 4. 实施路线（分阶段）

### P0：稳定兜底（已落地）

- YouTube 下载默认走 `youtubedl-android`，避免 Android 上直接执行 Linux 二进制失败
- 当配置 `quicktranslate.ytdlp.path` 时，切换为外部 `yt-dlp` 执行（调试/实验用途）
- 失败信息继续透出完整输出，支持 UI 复制

### P1：源码构建链路（可选增强）

- 新增脚本：`tools/build_embedded_ytdlp.sh`
- 输入：`yt-dlp` 源码版本 + Android NDK + 构建配置
- 输出：
  - `app/src/main/jniLibs/arm64-v8a/libytdlp.so`（运行时优先）
  - `app/src/main/whisper-assets/tools/yt-dlp/arm64-v8a/yt-dlp`（兜底）

当前实现（T12）：

- 已提供 `tools/build_embedded_ytdlp.sh` 作为统一产物脚本
- 支持两种输入：
  - `--from-android-binary <path>`
  - `--download-url <url>`（仅用于 Android 兼容二进制链接）
- 会校验 `aarch64` 架构，并拒绝明显的 `GNU/Linux` 目标
- 输出构建清单：`tools/.cache/ytdlp-build-manifest.txt`

### P2：运行时执行链路（必做）

- `EmbeddedYtDlpProvider`：
  - 优先 `nativeLibraryDir/libytdlp.so`
  - 次优 assets 解包文件
  - 最后 `quicktranslate.ytdlp.path`
- `SessionRemoteMediaDownloadStage`：
  - YouTube 链接仅走 `yt-dlp`
  - 不静默回退直链下载
  - 保留完整 stderr 到任务错误信息

### P3：可运维化（建议）

- 增加启动自检：
  - 检查 `libytdlp.so` 是否存在
  - 检查执行测试（`--version`）
- 首页/任务页增加诊断入口：
  - 一键复制 `yt-dlp` 版本与执行路径

---

## 5. 验收标准

1. 同一 YouTube 链接在新装 App 首次导入可直接下载（默认不依赖外部路径）
2. 不出现 `yt-dlp is unavailable` 泛化错误，必须有具体原因
3. 失败信息可在 UI 复制
4. `./gradlew :app:assembleDebug` 通过

---

## 6. 风险与说明

- `yt-dlp` 本质是高变动项目，需定期版本跟进
- 若 YouTube 策略变化，需优先更新内置 yt-dlp 版本
- 多 ABI（x86_64/armeabi-v7a）暂不在本阶段覆盖

---

## 7. 下一步落地顺序

1. 先完成 `P1` 脚本与产物验证（命令行可跑 `--version`）
2. 再接 `P2` 到 App 下载链路
3. 最后补 `P3` 自检与诊断 UI

---

## 8. T12 执行命令示例

```bash
tools/build_embedded_ytdlp.sh \
  --from-android-binary /absolute/path/to/android-arm64-yt-dlp \
  --version 2026.03.17
```

执行后应看到：

- `app/src/main/jniLibs/arm64-v8a/libytdlp.so`
- `app/src/main/whisper-assets/tools/yt-dlp/arm64-v8a/yt-dlp`
- `tools/.cache/ytdlp-build-manifest.txt`
