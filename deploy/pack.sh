#!/usr/bin/env bash
# ============================================================
# pack.sh — 在 macOS / Linux 本地构建后端 jar 与前端 dist，并归集到 deploy 部署包
# （与 Windows 下的 pack.ps1 等价）
# 用法：
#   ./pack.sh                    # 全量构建（后端 + 前端）并打包
#   ./pack.sh --skip-backend     # 只用现有 target 里的 jar
#   ./pack.sh --skip-frontend    # 只用现有 dist
#   ./pack.sh --skip-build       # 不重新编译，仅拷贝现有产物
# ============================================================
set -euo pipefail

# macOS 自带 bash 3.2，注意不要使用 bash 4+ 语法
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
UI_DIR="$ROOT_DIR/cangjie-agent-ui"

SKIP_BACKEND=0
SKIP_FRONTEND=0
SKIP_BUILD=0

for arg in "$@"; do
  case "$arg" in
    --skip-backend)   SKIP_BACKEND=1 ;;
    --skip-frontend)  SKIP_FRONTEND=1 ;;
    --skip-build)     SKIP_BUILD=1 ;;
    -h|--help)
      sed -n '2,11p' "$0"
      exit 0
      ;;
    *) echo "未知参数：$arg" >&2; exit 1 ;;
  esac
done

echo "==================== 打制品包 ===================="

# ---------- 环境检查 ----------
if [ "$SKIP_BACKEND" -eq 0 ] && [ "$SKIP_BUILD" -eq 0 ]; then
  command -v mvn >/dev/null 2>&1 || {
    echo "未找到 mvn。macOS 可执行：brew install maven" >&2
    echo "并确认 JDK 17 已安装：java -version" >&2
    exit 1
  }
fi
if [ "$SKIP_FRONTEND" -eq 0 ] && [ "$SKIP_BUILD" -eq 0 ]; then
  command -v npm >/dev/null 2>&1 || {
    echo "未找到 npm。macOS 可执行：brew install node" >&2
    exit 1
  }
fi

# ---------- 后端 ----------
if [ "$SKIP_BACKEND" -eq 0 ]; then
  if [ "$SKIP_BUILD" -eq 0 ]; then
    echo ">>> 构建后端（mvn package -DskipTests）..."
    ( cd "$ROOT_DIR" && mvn clean package -DskipTests -pl cangjie-start -am -B )
  fi
  JAR="$ROOT_DIR/cangjie-start/target/cangjie-start.jar"
  [ -f "$JAR" ] || { echo "未找到后端产物：$JAR" >&2; exit 1; }
  mkdir -p "$SCRIPT_DIR/backend"
  cp -f "$JAR" "$SCRIPT_DIR/backend/cangjie-start.jar"
  echo ">>> 后端制品已就位：backend/cangjie-start.jar ($(du -m "$JAR" | cut -f1) MB)"
fi

# ---------- 前端 ----------
if [ "$SKIP_FRONTEND" -eq 0 ]; then
  if [ "$SKIP_BUILD" -eq 0 ]; then
    echo ">>> 构建前端（npm run build）..."
    cd "$UI_DIR"
    [ -d node_modules ] || npm install
    npm run build
    cd "$SCRIPT_DIR"
  fi
  [ -d "$UI_DIR/dist/admin" ] && [ -d "$UI_DIR/dist/chat" ] || {
    echo "未找到前端产物：$UI_DIR/dist/admin 或 dist/chat（请先执行 npm run build）" >&2
    exit 1
  }
  rm -rf "$SCRIPT_DIR/frontend/dist"
  mkdir -p "$SCRIPT_DIR/frontend/dist"
  cp -R "$UI_DIR/dist/." "$SCRIPT_DIR/frontend/dist/"
  echo ">>> 前端制品已就位：frontend/dist（admin + chat）"
fi

echo ""
echo "制品打包完成。接下来的操作："
echo "  1) 将整个 deploy 文件夹上传到 Linux 服务器（无需上传源码）"
echo "  2) 服务器上执行：chmod +x *.sh && sudo ./deploy-all.sh"
echo "  若想在 macOS 本机跑整套服务，请先装 Docker Desktop，然后："
echo "     chmod +x *.sh && ./02-init.sh && ./03-build-images.sh && ./04-start.sh"
