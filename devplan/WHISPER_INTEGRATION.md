# Whisper 接入流程说明（Android 本地转写）

## 1. 目标

在 App 内置 `whisper.cpp` 能力，实现离线本地英文转写，不依赖服务端。

当前实现支持：

- `mock` / `real` 模式切换
- APK 内置模型与二进制
- 16KB page-size 设备兼容
- 常见动态库依赖问题处理

---

## 2. 关键文件

- 构建与产物脚本：`tools/setup_embedded_whisper.sh`
- Session 侧 DI：`feature/session/.../di/SessionModule.kt`
- 运行时配置恢复：`feature/session/.../transcription/EmbeddedWhisperConfigProvider.kt`
- 真实转写实现：`feature/transcription/.../WhisperCliTranscriptionService.kt`
- 应用打包配置：`app/build.gradle.kts`
- 转写模式配置：`gradle.properties`

---

## 3. 配置项

`gradle.properties`：

- `quicktranslate.transcription.mode=real`：启用真实转写
- `quicktranslate.whisper.cli.path=`：可选，外部 CLI 路径（不填则走内置）
- `quicktranslate.whisper.model.path=`：可选，外部模型路径（不填则走内置）
- `quicktranslate.whisper.language=en`

说明：

- 推荐默认走“内置二进制 + 内置模型”。
- `real` 初始化失败时，当前逻辑会降级到 `mock`，避免应用崩溃。

---

## 4. 一键准备产物

执行：

```bash
HTTPS_PROXY=http://127.0.0.1:7890 \
HTTP_PROXY=http://127.0.0.1:7890 \
ALL_PROXY=socks5://127.0.0.1:7890 \
./tools/setup_embedded_whisper.sh
```

脚本会完成：

1. 下载/复用 Android NDK（`r27c`）
2. 下载 `whisper.cpp` 源码（当前 `v1.8.4`）
3. 编译 `arm64-v8a` 版 `whisper-cli`
4. 下载模型 `ggml-tiny.en.bin`
5. 复制产物到：
   - `app/src/main/whisper-assets/whisper/models/...`
   - `app/src/main/jniLibs/arm64-v8a/...`

---

## 5. 打包约束

### 5.1 16KB 对齐

脚本在链接阶段使用：

- `-Wl,-z,max-page-size=16384`
- `-Wl,-z,common-page-size=16384`

并在脚本内用 `llvm-readelf` 校验 `LOAD Align = 0x4000`。

### 5.2 JNI 打包策略

`app/build.gradle.kts` 中需要：

- `sourceSets.main.jniLibs.srcDirs("src/main/jniLibs")`
- `packaging.jniLibs.useLegacyPackaging = true`

---

## 6. 运行时流程（real 模式）

1. `SessionModule` 创建 `TranscriptionService`
2. `EmbeddedWhisperConfigProvider` 优先从 `nativeLibraryDir` 读取 `libwhisper_cli.so`
3. 若 `nativeLibraryDir` 无库，则从 `base.apk` 的 `lib/<abi>/` 提取一组 so 到本地目录
4. `WhisperCliTranscriptionService` 通过 `/system/bin/linker64` 启动 `libwhisper_cli.so`
5. 传入 `-m/-f/-l/-osrt/-of` 参数生成 `.srt`
6. `SrtParser` 解析字幕并回写业务层

---

## 7. 常见问题排查

### 7.1 `error=13, Permission denied`

原因：从 `filesDir` 直接执行可执行文件，遇到 `noexec`。

处理：

- 优先执行 `nativeLibraryDir/libwhisper_cli.so`
- 使用 `linker64` 启动 `.so`，不要直接 `exec` 普通路径可执行文件

### 7.2 `library "libwhisper.so" not found`

原因：只打了 `libwhisper_cli.so`，未打依赖。

处理：同时打包以下库：

- `libwhisper.so`
- `libggml.so`
- `libggml-cpu.so`
- `libggml-base.so`

### 7.3 `library "libomp.so" not found`

原因：`libggml-cpu.so` 开启 OpenMP，动态依赖 `libomp.so`。

处理：

- 编译时关闭 OpenMP：`-DGGML_OPENMP=OFF`
- 清理 build 目录后重新生成产物，防止旧缓存残留

### 7.4 转写成功但无字幕

常见于素材语音不清晰、背景噪声大、语言与模型不匹配。

当前 UI 已增加引导卡片，建议用户：

- 重试
- 更换文件
- 使用更清晰片段（10-30 秒）

---

## 8. 验证命令

```bash
./gradlew :app:assembleDebug
```

检查 APK 内容：

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | rg "lib/arm64-v8a|assets/whisper"
```

检查动态依赖：

```bash
llvm-readelf -d app/src/main/jniLibs/arm64-v8a/libggml-cpu.so | rg NEEDED
```

---

## 9. 后续建议

- 增加 `arm64-v8a` 之外 ABI 策略（或明确仅真机 arm64 支持）
- 提供模型档位切换（`tiny/base/small`）
- 增加转写诊断日志开关（便于线上问题定位）
