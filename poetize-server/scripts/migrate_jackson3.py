#!/usr/bin/env python3
"""
Jackson 2.x -> 3.x 包名迁移脚本

Spring Boot 4.0 使用 Jackson 3.x，包名从 com.fasterxml.jackson 变更为 tools.jackson
此脚本自动替换项目中的所有 Jackson 引用
"""

import os
import re
from pathlib import Path

# 定义替换规则
REPLACEMENTS = [
    # 注解包
    ("com.fasterxml.jackson.annotation", "tools.jackson.annotation"),
    # 核心包
    ("com.fasterxml.jackson.core", "tools.jackson.core"),
    # 数据绑定包
    ("com.fasterxml.jackson.databind", "tools.jackson.databind"),
    # 数据类型包
    ("com.fasterxml.jackson.datatype", "tools.jackson.datatype"),
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
            print(f"  ✅ 已修改: {file_path.relative_to(PROJECT_ROOT.parent.parent)} ({file_replacements} 处替换)")
            return True
        except Exception as e:
            print(f"  ❌ 写入失败: {e}")
            return False
    
    return False


def main():
    print("=" * 60)
    print("Jackson 2.x -> 3.x 包名迁移脚本")
    print("=" * 60)
    print(f"\n📁 扫描目录: {PROJECT_ROOT}")
    print("\n🔄 替换规则:")
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
    print("📊 迁移统计:")
    print(f"   扫描文件数: {stats['files_scanned']}")
    print(f"   修改文件数: {stats['files_modified']}")
    print(f"   替换总数: {stats['replacements_made']}")
    print("=" * 60)

    if stats["replacements_made"] > 0:
        print("\n✅ Jackson 包名迁移完成！")
        print("⚠️ 请运行 'mvn clean compile' 验证编译是否通过")
    else:
        print("\n💡 未发现需要替换的内容")

    return 0


if __name__ == "__main__":
    exit(main())
