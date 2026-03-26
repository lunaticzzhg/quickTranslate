#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_DIR="${ROOT_DIR}/tools/.cache"
ASSET_DIR="${ROOT_DIR}/app/src/main/whisper-assets/tools/yt-dlp/arm64-v8a"
ASSET_TARGET="${ASSET_DIR}/yt-dlp"
JNI_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"
JNI_TARGET="${JNI_DIR}/libytdlp.so"
MANIFEST_PATH="${ROOT_DIR}/tools/.cache/ytdlp-build-manifest.txt"

print_usage() {
  cat <<'EOF'
Usage:
  tools/build_embedded_ytdlp.sh --from-android-binary /absolute/path/to/yt-dlp [--version 2026.03.17]
  tools/build_embedded_ytdlp.sh --download-url <url> [--version <version-tag>]

Description:
  Prepare embedded yt-dlp runtime artifacts for Android arm64.
  Outputs:
    - app/src/main/whisper-assets/tools/yt-dlp/arm64-v8a/yt-dlp
    - app/src/main/jniLibs/arm64-v8a/libytdlp.so

Notes:
  - Input binary must be Android-compatible arm64 executable.
  - Linux glibc/musl binaries are not compatible with Android runtime.
EOF
}

ANDROID_BINARY=""
DOWNLOAD_URL=""
VERSION_TAG="unknown"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from-android-binary)
      ANDROID_BINARY="${2:-}"
      shift 2
      ;;
    --download-url)
      DOWNLOAD_URL="${2:-}"
      shift 2
      ;;
    --version)
      VERSION_TAG="${2:-unknown}"
      shift 2
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      print_usage
      exit 1
      ;;
  esac
done

if [[ -n "${ANDROID_BINARY}" && -n "${DOWNLOAD_URL}" ]]; then
  echo "Use only one input mode: --from-android-binary or --download-url."
  exit 1
fi

mkdir -p "${CACHE_DIR}" "${ASSET_DIR}" "${JNI_DIR}"

if [[ -n "${DOWNLOAD_URL}" ]]; then
  ANDROID_BINARY="${CACHE_DIR}/yt-dlp-android-arm64"
  echo "Downloading yt-dlp from: ${DOWNLOAD_URL}"
  curl -L --fail --retry 3 --connect-timeout 15 --max-time 600 \
    "${DOWNLOAD_URL}" -o "${ANDROID_BINARY}"
fi

if [[ -z "${ANDROID_BINARY}" ]]; then
  echo "Missing input. Provide --from-android-binary or --download-url."
  print_usage
  exit 1
fi

if [[ ! -f "${ANDROID_BINARY}" ]]; then
  echo "Binary not found: ${ANDROID_BINARY}"
  exit 1
fi

FILE_DESC="$(file "${ANDROID_BINARY}")"
echo "Input binary: ${FILE_DESC}"
if [[ "${FILE_DESC}" != *"ARM aarch64"* ]]; then
  echo "Expected arm64/aarch64 binary. Got: ${FILE_DESC}"
  exit 1
fi
if [[ "${FILE_DESC}" == *"GNU/Linux"* ]]; then
  echo "Detected GNU/Linux binary (not Android). Please provide Android-compatible binary."
  exit 1
fi

cp "${ANDROID_BINARY}" "${ASSET_TARGET}"
cp "${ANDROID_BINARY}" "${JNI_TARGET}"
chmod +x "${ASSET_TARGET}" "${JNI_TARGET}"

SHA256_SUM="$(shasum -a 256 "${ANDROID_BINARY}" | awk '{print $1}')"
{
  echo "version=${VERSION_TAG}"
  echo "source=${ANDROID_BINARY}"
  echo "sha256=${SHA256_SUM}"
  echo "built_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "asset_target=${ASSET_TARGET}"
  echo "jni_target=${JNI_TARGET}"
} > "${MANIFEST_PATH}"

echo "Embedded yt-dlp artifacts updated:"
echo "  ${ASSET_TARGET}"
echo "  ${JNI_TARGET}"
echo "Manifest:"
echo "  ${MANIFEST_PATH}"
