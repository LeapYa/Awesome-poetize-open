# ============================================================
# sync-poetry-old.ps1 — 从 poetry.sql 重新生成 poetry_old.sql
#
# poetry_old.sql 是 poetry.sql 的"全 InnoDB"兼容变体（MySQL / 低版本
# MariaDB），两者唯一允许的差异：
#   1. ENGINE=Aria / ENGINE=RocksDB → ENGINE=InnoDB
#   2. RAG 表注释改为 MySQL 场景说明
#
# 禁止手工编辑 poetry_old.sql！任何 poetry.sql 改动后运行本脚本同步：
#   pwsh poetize-server/scripts/sync-poetry-old.ps1
#
# pre-push 钩子会校验两文件一致性，不同步时阻断推送。
# ============================================================

$ErrorActionPreference = 'Stop'

$sqlDir  = Join-Path $PSScriptRoot '..\sql'
$srcFile = Join-Path $sqlDir 'poetry.sql'
$dstFile = Join-Path $sqlDir 'poetry_old.sql'

Write-Host "[sync-poetry-old] 读取源文件: $srcFile"
# 保持 UTF-8 无 BOM 编码，避免中文注释乱码
$utf8 = [System.Text.UTF8Encoding]::new($false)
$text = [System.IO.File]::ReadAllText($srcFile, $utf8)

# 差异1：存储引擎全部替换为 InnoDB
$engineMatches = ([regex]::Matches($text, 'ENGINE=(Aria|RocksDB) ')).Count
$text = $text -replace 'ENGINE=Aria ', 'ENGINE=InnoDB ' -replace 'ENGINE=RocksDB ', 'ENGINE=InnoDB '
Write-Host "[sync-poetry-old] 已替换 $engineMatches 处 Aria/RocksDB 引擎为 InnoDB"

# 差异2：RAG 表注释改为 MySQL 场景说明
$ragSrc = '-- RAG 知识文档元数据表（MariaDB 向量检索）'
$ragDst = '-- RAG 知识文档元数据表（兼容初始化；MySQL 场景默认关闭向量检索）'
if (-not $text.Contains($ragSrc)) {
    Write-Warning "[sync-poetry-old] 未找到 RAG 注释源文本，poetry.sql 可能已调整该注释，请同步更新本脚本"
}
$text = $text.Replace($ragSrc, $ragDst)

[System.IO.File]::WriteAllText($dstFile, $text, $utf8)
Write-Host "[sync-poetry-old] 已生成: $dstFile"
Write-Host "[sync-poetry-old] 完成。请将两个文件一并提交。"
