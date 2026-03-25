#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_DIR="${ROOT_DIR}/tools/.cache"
ASSETS_DIR="${ROOT_DIR}/app/src/main/whisper-assets/whisper"
JNI_LIB_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"
NDK_VERSION="r27c"
WHISPER_VERSION="v1.8.4"
MODEL_NAME="ggml-tiny.en.bin"

mkdir -p "${CACHE_DIR}" "${ASSETS_DIR}/bin/arm64-v8a" "${ASSETS_DIR}/models" "${JNI_LIB_DIR}"

download_file() {
  local url="$1"
  local output="$2"
  curl --http1.1 -L --fail --retry 5 --retry-delay 2 --retry-connrefused \
    "${url}" -o "${output}"
}

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Only macOS is supported by this helper script."
  exit 1
fi

if [[ "$(uname -m)" != "arm64" ]]; then
  echo "This script currently expects Apple Silicon host (arm64)."
  exit 1
fi

NDK_ZIP="${CACHE_DIR}/android-ndk-${NDK_VERSION}-darwin.zip"
NDK_DIR="${CACHE_DIR}/android-ndk-${NDK_VERSION}"
if [[ ! -d "${NDK_DIR}" ]]; then
  echo "[1/5] Downloading Android NDK ${NDK_VERSION}..."
  download_file \
    "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-darwin.zip" \
    "${NDK_ZIP}"
  echo "[2/5] Extracting Android NDK..."
  unzip -q -o "${NDK_ZIP}" -d "${CACHE_DIR}"
fi

WHISPER_TAR="${CACHE_DIR}/whisper-${WHISPER_VERSION}.tar.gz"
WHISPER_SRC_DIR="${CACHE_DIR}/whisper.cpp-${WHISPER_VERSION#v}"
if [[ ! -d "${WHISPER_SRC_DIR}" ]]; then
  echo "[3/5] Downloading whisper.cpp ${WHISPER_VERSION}..."
  download_file \
    "https://github.com/ggml-org/whisper.cpp/archive/refs/tags/${WHISPER_VERSION}.tar.gz" \
    "${WHISPER_TAR}"
  tar -xzf "${WHISPER_TAR}" -C "${CACHE_DIR}"
fi

WHISPER_BUILD_DIR="${CACHE_DIR}/build-whisper-android-arm64"
LINKER_PAGE_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
echo "[4/5] Building whisper-cli for Android arm64-v8a..."
rm -rf "${WHISPER_BUILD_DIR}"
cmake \
  -S "${WHISPER_SRC_DIR}" \
  -B "${WHISPER_BUILD_DIR}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="${NDK_DIR}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_static \
  -DCMAKE_EXE_LINKER_FLAGS="${LINKER_PAGE_FLAGS}" \
  -DCMAKE_SHARED_LINKER_FLAGS="${LINKER_PAGE_FLAGS}" \
  -DGGML_OPENMP=OFF \
  -DWHISPER_BUILD_TESTS=OFF \
  -DWHISPER_BUILD_EXAMPLES=ON
cmake --build "${WHISPER_BUILD_DIR}" --target whisper-cli -j

BIN_CANDIDATE="${WHISPER_BUILD_DIR}/bin/whisper-cli"
if [[ ! -f "${BIN_CANDIDATE}" ]]; then
  BIN_CANDIDATE="$(find "${WHISPER_BUILD_DIR}" -type f -name whisper-cli | head -n 1)"
fi
if [[ -z "${BIN_CANDIDATE}" || ! -f "${BIN_CANDIDATE}" ]]; then
  echo "Failed to locate built whisper-cli binary."
  exit 1
fi

cp "${BIN_CANDIDATE}" "${ASSETS_DIR}/bin/arm64-v8a/whisper-cli"
chmod +x "${ASSETS_DIR}/bin/arm64-v8a/whisper-cli"
cp "${BIN_CANDIDATE}" "${JNI_LIB_DIR}/libwhisper_cli.so"
chmod +x "${JNI_LIB_DIR}/libwhisper_cli.so"
cp "${WHISPER_BUILD_DIR}/src/libwhisper.so" "${JNI_LIB_DIR}/libwhisper.so"
cp "${WHISPER_BUILD_DIR}/ggml/src/libggml.so" "${JNI_LIB_DIR}/libggml.so"
cp "${WHISPER_BUILD_DIR}/ggml/src/libggml-cpu.so" "${JNI_LIB_DIR}/libggml-cpu.so"
cp "${WHISPER_BUILD_DIR}/ggml/src/libggml-base.so" "${JNI_LIB_DIR}/libggml-base.so"

READELF_BIN="${NDK_DIR}/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
if [[ -x "${READELF_BIN}" ]]; then
  if ! "${READELF_BIN}" -l "${ASSETS_DIR}/bin/arm64-v8a/whisper-cli" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "whisper-cli is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
  if ! "${READELF_BIN}" -l "${JNI_LIB_DIR}/libwhisper_cli.so" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "libwhisper_cli.so is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
  if ! "${READELF_BIN}" -l "${JNI_LIB_DIR}/libwhisper.so" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "libwhisper.so is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
  if ! "${READELF_BIN}" -l "${JNI_LIB_DIR}/libggml.so" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "libggml.so is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
  if ! "${READELF_BIN}" -l "${JNI_LIB_DIR}/libggml-cpu.so" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "libggml-cpu.so is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
  if ! "${READELF_BIN}" -l "${JNI_LIB_DIR}/libggml-base.so" \
    | grep "LOAD" | grep -q "0x4000"; then
    echo "libggml-base.so is not 16KB page aligned (expected LOAD align 0x4000)."
    exit 1
  fi
fi

MODEL_PATH="${ASSETS_DIR}/models/${MODEL_NAME}"
if [[ ! -f "${MODEL_PATH}" ]]; then
  echo "[5/5] Downloading model ${MODEL_NAME}..."
  download_file \
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${MODEL_NAME}" \
    "${MODEL_PATH}"
fi

echo "Done."
echo "Embedded binary: ${ASSETS_DIR}/bin/arm64-v8a/whisper-cli"
echo "Embedded native binary: ${JNI_LIB_DIR}/libwhisper_cli.so"
echo "Embedded model: ${MODEL_PATH}"
