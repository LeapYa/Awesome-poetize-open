#!/usr/bin/env bash
# ============================================================
# sync-poetry-old.sh — 从 poetry.sql 重新生成 poetry_old.sql（Mac/Linux 版）
#
# poetry_old.sql 是 poetry.sql 的"全 InnoDB"兼容变体（MySQL / 低版本
# MariaDB），两者唯一允许的差异：
#   1. ENGINE=Aria / ENGINE=RocksDB → ENGINE=InnoDB
#   2. RAG 表注释改为 MySQL 场景说明
#
# 禁止手工编辑 poetry_old.sql！任何 poetry.sql 改动后运行本脚本同步：
#   bash poetize-server/scripts/sync-poetry-old.sh      # Mac/Linux
#   pwsh poetize-server/scripts/sync-poetry-old.ps1     # Windows（等价脚本）
#
# pre-push 钩子会校验两文件一致性，不同步时阻断推送。
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_FILE="$SCRIPT_DIR/../sql/poetry.sql"
DST_FILE="$SCRIPT_DIR/../sql/poetry_old.sql"

echo "[sync-poetry-old] 读取源文件: $SRC_FILE"

# 统计引擎替换次数（日志反馈，与 PowerShell 版一致）
ENGINE_COUNT=$(grep -cE 'ENGINE=(Aria|RocksDB) ' "$SRC_FILE" || true)

# 差异1：存储引擎全部替换为 InnoDB
# 差异2：RAG 表注释改为 MySQL 场景说明
# 说明：sed 按字节处理，中文注释无需 locale 支持；sed 重写不会添加 BOM，
# 与 UTF-8 无 BOM 的源文件保持一致。管道写文件（非 sed -i），对
# GNU sed（Linux）与 BSD sed（macOS）行为一致。
sed -e 's/ENGINE=Aria /ENGINE=InnoDB /g' \
    -e 's/ENGINE=RocksDB /ENGINE=InnoDB /g' \
    -e 's/-- RAG 知识文档元数据表（MariaDB 向量检索）/-- RAG 知识文档元数据表（兼容初始化；MySQL 场景默认关闭向量检索）/' \
    "$SRC_FILE" > "$DST_FILE"

echo "[sync-poetry-old] 已替换 $ENGINE_COUNT 处 Aria/RocksDB 引擎为 InnoDB"
echo "[sync-poetry-old] 已生成: $DST_FILE"
echo "[sync-poetry-old] 完成。请将两个文件一并提交。"
