#!/usr/bin/env python3
"""
Jackson 3.x -> 2.x 包名回退脚本

Spring Boot 4.0 仍然支持 Jackson 2.x，且注解包保持 com.fasterxml.jackson.core 包名
此脚本回退之前的 Jackson 3 迁移
"""

import os
from pathlib import Path

# 定义回退规则（与迁移脚本相反）
REPLACEMENTS = [
    # 注解包
    ("tools.jackson.annotation", "com.fasterxml.jackson.annotation"),
    # 核心包
    ("tools.jackson.core", "com.fasterxml.jackson.core"),
    # 数据绑定包
    ("tools.jackson.databind", "com.fasterxml.jackson.databind"),
    # 数据类型包
    ("tools.jackson.datatype", "com.fasterxml.jackson.datatype"),
]

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent / "poetry-web" / "src"

# 统计
stats = {
    "files_scanned": 0,
    "files_modified": 0,
    "replacements_made": 0,
}


def process_file(file_path: Path) -> bool:
    """处理单个文件，返回是否有修改"""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception as e:
        print(f"  ⚠️ 无法读取文件: {e}")
        return False

    original_content = content
    file_replacements = 0

    for old, new in REPLACEMENTS:
        count = content.count(old)
        if count > 0:
            content = content.replace(old, new)
            file_replacements += count
            stats["replacements_made"] += count

    if content != original_content:
        try:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"  ✅ 已回退: {file_path.relative_to(PROJECT_ROOT.parent.parent)} ({file_replacements} 处)")
            return True
        except Exception as e:
            print(f"  ❌ 写入失败: {e}")
            return False
    
    return False


def main():
    print("=" * 60)
    print("Jackson 3.x -> 2.x 包名回退脚本")
    print("=" * 60)
    print(f"\n📁 扫描目录: {PROJECT_ROOT}")
    print("\n🔄 回退规则:")
    for old, new in REPLACEMENTS:
        print(f"   {old} → {new}")
    print()

    # 检查目录是否存在
    if not PROJECT_ROOT.exists():
        print(f"❌ 错误: 目录不存在 - {PROJECT_ROOT}")
        return 1

    # 扫描所有 Java 文件
    java_files = list(PROJECT_ROOT.rglob("*.java"))
    print(f"📝 找到 {len(java_files)} 个 Java 文件\n")

    for file_path in java_files:
        stats["files_scanned"] += 1
        if process_file(file_path):
            stats["files_modified"] += 1

    # 打印统计
    print("\n" + "=" * 60)
    print("📊 回退统计:")
    print(f"   扫描文件数: {stats['files_scanned']}")
    print(f"   修改文件数: {stats['files_modified']}")
    print(f"   回退总数: {stats['replacements_made']}")
    print("=" * 60)

    if stats["replacements_made"] > 0:
        print("\n✅ Jackson 包名回退完成！")
    else:
        print("\n💡 未发现需要回退的内容")

    return 0


if __name__ == "__main__":
    exit(main())
