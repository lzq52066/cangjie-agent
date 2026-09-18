#!/usr/bin/env bash
# ============================================================
# 一键串跑：装 Docker → 初始化 → 构建 → 启动 → 状态
# 用法：sudo ./deploy-all.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

echo "==================== CangJie Agent 一键部署 ===================="

run_step() {
  local name="$1"; shift
  echo ""
  echo ">>> $name"
  if ! "$@"; then
    echo ""
    echo "!!! 步骤「$name」执行失败，部署已中断。" >&2
    echo "    本脚本各步骤幂等，修复后可直接重新执行，已完成的步骤会自动跳过。" >&2
    exit 1
  fi
}

if [ "$(id -u)" -eq 0 ]; then
  run_step "步骤 1/5：安装 Docker" ./01-install-docker.sh
else
  echo ""
  echo ">>> 步骤 1/5：安装 Docker"
  echo "    当前非 root，跳过 Docker 安装（如未安装请用 sudo 重新执行）"
fi

run_step "步骤 2/5：初始化" ./02-init.sh

run_step "步骤 3/5：构建镜像" ./03-build-images.sh

run_step "步骤 4/5：启动" ./04-start.sh

run_step "步骤 5/5：状态" ./05-status.sh

echo ""
echo "==================== 部署流程结束 ===================="
