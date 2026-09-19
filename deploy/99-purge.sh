#!/usr/bin/env bash
# ============================================================
# 99 - 卸载（删除本项目的容器与网络；数据目录 ./data 默认保留）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

echo "[99] 将删除本项目所有容器与网络。"
echo "     数据目录 ./data（数据库/对象存储/日志）默认保留，便于备份。"
read -p "[99] 确认卸载请输入 yes：" confirm
if [ "$confirm" != "yes" ]; then
  echo "[99] 已取消"
  exit 0
fi

docker compose down
echo "[99] 已删除容器与网络"

echo ""
read -p "[99] 是否同时删除数据目录 ./data（⚠ 数据不可恢复，输入 delete 确认）：" wipe
if [ "$wipe" = "delete" ]; then
  rm -rf data
  echo "[99] 已删除数据目录 ./data"
else
  echo "[99] 数据目录 ./data 已保留，重新执行 ./deploy-all.sh 可恢复服务"
fi
