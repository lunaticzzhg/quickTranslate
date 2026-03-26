#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="${ROOT_DIR}/app/src/main/whisper-assets/tools/yt-dlp/arm64-v8a"
TARGET_PATH="${ASSET_DIR}/yt-dlp"
JNI_LIB_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"
JNI_TARGET_PATH="${JNI_LIB_DIR}/libytdlp.so"

print_usage() {
  cat <<'EOF'
Usage:
  tools/setup_embedded_ytdlp.sh --from /absolute/path/to/yt-dlp

Description:
  Copy a prepared Android arm64 yt-dlp executable into app assets so runtime can
  extract and use embedded yt-dlp automatically.
EOF
}

SOURCE_PATH=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --from)
      SOURCE_PATH="${2:-}"
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

if [[ -z "${SOURCE_PATH}" ]]; then
  echo "Missing --from argument."
  print_usage
  exit 1
fi

if [[ ! -f "${SOURCE_PATH}" ]]; then
  echo "File not found: ${SOURCE_PATH}"
  exit 1
fi

mkdir -p "${ASSET_DIR}"
cp "${SOURCE_PATH}" "${TARGET_PATH}"
chmod +x "${TARGET_PATH}"
mkdir -p "${JNI_LIB_DIR}"
cp "${SOURCE_PATH}" "${JNI_TARGET_PATH}"
chmod +x "${JNI_TARGET_PATH}"

echo "Embedded yt-dlp updated:"
echo "  ${TARGET_PATH}"
echo "  ${JNI_TARGET_PATH}"
