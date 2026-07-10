#!/bin/bash
# ============================================================
# Claude Dev Skills — 一键安装脚本
# 将 skills 安装到目标 Android 项目的 .claude/skills/ 目录
# ============================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_usage() {
    echo "用法: $0 <目标项目路径> [skill名称...]"
    echo ""
    echo "示例:"
    echo "  $0 ~/AndroidStudioProjects/MyApp                    # 安装全部 skills"
    echo "  $0 ~/AndroidStudioProjects/MyApp android-compose-expert login-register-flow  # 只安装指定 skills"
    echo ""
    echo "可用 skills:"
    echo "  android-compose-expert         Compose UI 开发专家"
    echo "  android-multi-flavor-google    多渠道 + Google 服务隔离"
    echo "  android-viewsystem-foundations ViewSystem 基础"
    echo "  kyc-workflow                   KYC 认证工作流"
    echo "  login-register-flow            登录注册流程"
    echo "  order-repay-extension-flow     订单还款 + 展期"
    echo "  project-bootstrap              项目初始化向导"
    echo "  swagger-to-kotlin              Swagger → Kotlin 代码生成"
    echo "  test-case-audit                测试用例走查"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -lt 1 ]; then
    echo -e "${RED}错误: 缺少目标项目路径${NC}"
    print_usage
    exit 1
fi

TARGET_PROJECT="$1"
shift

# 要安装的 skills 列表
ALL_SKILLS=(
    "android-compose-expert"
    "android-multi-flavor-google"
    "android-viewsystem-foundations"
    "kyc-workflow"
    "login-register-flow"
    "order-repay-extension-flow"
    "project-bootstrap"
    "swagger-to-kotlin"
    "test-case-audit"
)

if [ $# -gt 0 ]; then
    SKILLS_TO_INSTALL=("$@")
else
    SKILLS_TO_INSTALL=("${ALL_SKILLS[@]}")
fi

# 检查目标路径
if [ ! -d "$TARGET_PROJECT" ]; then
    echo -e "${RED}错误: 目标项目路径不存在: $TARGET_PROJECT${NC}"
    exit 1
fi

# 创建目标 .claude/skills 目录
SKILLS_DIR="$TARGET_PROJECT/.claude/skills"
mkdir -p "$SKILLS_DIR"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Claude Dev Skills 安装器${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "目标项目: $TARGET_PROJECT"
echo "Skills 目录: $SKILLS_DIR"
echo ""

INSTALLED=0
SKIPPED=0

for skill in "${SKILLS_TO_INSTALL[@]}"; do
    SRC="$SCRIPT_DIR/$skill"
    DST="$SKILLS_DIR/$skill"

    if [ ! -d "$SRC" ]; then
        echo -e "${YELLOW}⚠ 跳过: $skill (源目录不存在)${NC}"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if [ -d "$DST" ]; then
        echo -e "${YELLOW}⚠ $skill 已存在，覆盖中...${NC}"
        rm -rf "$DST"
    fi

    cp -r "$SRC" "$DST"
    echo -e "${GREEN}✓ 已安装: $skill${NC}"
    INSTALLED=$((INSTALLED + 1))
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成！${NC}"
echo -e "${GREEN}  成功: $INSTALLED 个 | 跳过: $SKIPPED 个${NC}"
echo -e "${GREEN}========================================${NC}"
