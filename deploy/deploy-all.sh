#!/usr/bin/env bash
# ============================================================
# 一键部署：装 Docker → 初始化 → 构建镜像 → 启动 → 查看状态
#
# 用法：
#   sudo ./deploy-all.sh     首次部署；更新制品后重新执行即可（幂等）
#   ./deploy-all.sh          已装好 Docker 且当前用户属于 docker 组时可直接执行
#
# 说明：本脚本不含任何交互，可安全地通过 nohup / CI 后台执行。
#       任一步骤失败即中断；修复后重新执行即可，已完成的步骤会自动跳过。
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

START_TS=$(date +%s)
STEP_NO=0
STEP_TOTAL=5

echo "============================================================"
echo " CangJie Agent 一键部署"
echo " 目录：$(pwd)"
echo " 时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"

run_step() {
  local name="$1"; shift
  local t0
  t0=$(date +%s)
  STEP_NO=$((STEP_NO + 1))
  echo ""
  echo ">>> 步骤 ${STEP_NO}/${STEP_TOTAL}：$name"
  if ! "$@"; then
    echo ""
    echo "!!! 步骤「$name」执行失败，部署已中断。" >&2
    echo "    所有步骤都是幂等的，修复问题后重新执行 ./deploy-all.sh 即可，" >&2
    echo "    已完成的步骤会自动跳过（已有 .env 与 ./data 不会被覆盖）。" >&2
    exit 1
  fi
  echo "    完成（耗时 $(( $(date +%s) - t0 )) 秒）"
}

# ---------- 1/5 Docker ----------
if [ "$(id -u)" -eq 0 ]; then
  run_step "安装 Docker" ./01-install-docker.sh
else
  STEP_NO=$((STEP_NO + 1))
  echo ""
  echo ">>> 步骤 ${STEP_NO}/${STEP_TOTAL}：安装 Docker"
  if docker compose version >/dev/null 2>&1; then
    echo "    当前非 root，Docker 已可用，跳过安装"
  else
    echo "    当前非 root 且 Docker 不可用，请改用：sudo ./deploy-all.sh" >&2
    exit 1
  fi
fi

# ---------- 2~5 ----------
run_step "初始化（数据目录 / .env / 制品校验）" ./02-init.sh
run_step "构建镜像" ./03-build-images.sh
run_step "启动服务" ./04-start.sh
run_step "查看状态" ./05-status.sh

echo ""
echo "============================================================"
echo " 部署完成，总耗时 $(( $(date +%s) - START_TS )) 秒"
echo "============================================================"
