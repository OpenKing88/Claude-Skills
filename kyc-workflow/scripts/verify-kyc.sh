#!/usr/bin/env bash
# =============================================================================
# KYC Verification Script v4.0
# Runs 6-step verification checklist on an Android project's KYC implementation.
#
# Usage: ./verify-kyc.sh <project_root>
# Example: ./verify-kyc.sh /path/to/android/project
#
# Output: [PASS] / [FAIL] / [WARN] + detail + fix suggestion for each step
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color
BOLD='\033[1m'

PASS="${GREEN}[PASS]${NC}"
FAIL="${RED}[FAIL]${NC}"
WARN="${YELLOW}[WARN]${NC}"

PROJECT="${1:-.}"

if [ ! -d "$PROJECT" ]; then
    echo "Error: Project directory '$PROJECT' not found"
    echo "Usage: $0 <project_root>"
    exit 1
fi

echo ""
echo "======================================================================"
echo "  KYC Verification Script v4.0"
echo "  Project: $(cd "$PROJECT" && pwd)"
echo "======================================================================"
echo ""

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
TOTAL=6

# ---------------------------------------------------------------------------
# Step 1: Interface Existence Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 1/6: Interface Existence Check${NC}"
echo "----------------------------------------"

check_interface() {
    local name="$1"
    local pattern="$2"
    local result

    result=$(grep -r "$pattern" "$PROJECT" --include="*.kt" -l 2>/dev/null | head -3 || true)

    if [ -n "$result" ]; then
        echo -e "  ${PASS} $name found in:"
        echo "$result" | while read -r f; do echo "       $f"; done
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "  ${FAIL} $name NOT FOUND — $3"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

check_interface "submitAcqData" "submitAcq\|submitAcp\|submitAcquisition" "Define the acquisition data submit endpoint"
check_interface "faceCompare" "faceCompare\|face_check\|faceVerify" "Define the face comparison endpoint"
check_interface "ocrVerify" "ocrVerify\|ocr_verify\|ocrRecognize" "Define the OCR verification endpoint"
check_interface "bankList" "bankList\|getBankList\|queryBank" "Define the bank list query endpoint"
check_interface "dictList" "dictList\|getDict\|dataDict\|dictionary" "Define the dictionary query endpoint"
check_interface "acpElementInfo" "acpElement\|elementInfo\|pageInfo\|acqElement" "Define the page element query endpoint"
check_interface "areaList" "areaList\|getArea\|provinceList" "Define the area list endpoint"

echo ""

# ---------------------------------------------------------------------------
# Step 2: Cache Model Safety Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 2/6: Cache Model Safety Check${NC}"
echo "----------------------------------------"

# Find KYC cache data classes
CACHE_FILES=$(grep -r "Cache\|cache" "$PROJECT" --include="*.kt" -l 2>/dev/null | grep -i "kyc\|acq\|personal\|contact\|identity\|bank\|credit\|face" | head -10 || true)

if [ -z "$CACHE_FILES" ]; then
    echo -e "  ${WARN} No KYC cache data classes found — if KYC is implemented, caching may be missing"
    WARN_COUNT=$((WARN_COUNT + 1))
else
    echo "  Found cache files:"
    echo "$CACHE_FILES" | while read -r f; do echo "    $f"; done
    echo ""

    # Check for nullable fields
    NON_NULL_FIELDS=$(grep -r "val [a-zA-Z]*: String[^?]" $CACHE_FILES 2>/dev/null | grep -v "//\|/\*" || true)
    if [ -n "$NON_NULL_FIELDS" ]; then
        echo -e "  ${FAIL} Non-nullable fields found in cache models (should be 'String? = null'):"
        echo "$NON_NULL_FIELDS" | while read -r line; do echo "       $line"; done
        echo "  Fix: Change all cache fields to nullable types with default null"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    else
        echo -e "  ${PASS} Cache model fields appear to be nullable"
        PASS_COUNT=$((PASS_COUNT + 1))
    fi

    # Check for try/catch in cache read
    if grep -q "try\|catch\|runCatching" $CACHE_FILES 2>/dev/null; then
        echo -e "  ${PASS} Cache read has try/catch protection"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "  ${WARN} Cache read may lack try/catch — deserialization failure could crash"
        WARN_COUNT=$((WARN_COUNT + 1))
    fi
fi

echo ""

# ---------------------------------------------------------------------------
# Step 3: Image Processing Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 3/6: Image Processing Check${NC}"
echo "----------------------------------------"

if grep -rq "ImageCompress\|imageCompress\|compressImage\|BitmapCompress" "$PROJECT" --include="*.kt" 2>/dev/null; then
    COMPRESS_FILES=$(grep -r "ImageCompress\|imageCompress\|compressImage\|BitmapCompress" "$PROJECT" --include="*.kt" -l 2>/dev/null | head -3)
    echo -e "  ${PASS} Image compression utility found:"
    echo "$COMPRESS_FILES" | while read -r f; do echo "       $f"; done
    PASS_COUNT=$((PASS_COUNT + 1))

    # Check IO thread usage
    if grep -rq "Dispatchers.IO\|withContext.*IO\|ioDispatcher" $COMPRESS_FILES 2>/dev/null; then
        echo -e "  ${PASS} Image compression runs on IO thread (Dispatchers.IO)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "  ${WARN} Image compression may not use Dispatchers.IO — risk of ANR"
        WARN_COUNT=$((WARN_COUNT + 1))
    fi
else
    echo -e "  ${FAIL} No image compression utility found — images uploaded uncompressed"
    echo "  Fix: Create ImageCompressor with bounds detection + ByteArray decode + quality loop"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check for Coil/Glide usage in image display
if grep -rq "coil\|Coil\|Glide\|glide\|\.load(uri)" "$PROJECT" --include="*.kt" 2>/dev/null; then
    echo -e "  ${PASS} Image display uses Coil/Glide for URI loading (not raw BitmapFactory)"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "  ${WARN} Coil/Glide usage not detected — ensure BitmapFactory.decode is not used directly for image preview"
    WARN_COUNT=$((WARN_COUNT + 1))
fi

echo ""

# ---------------------------------------------------------------------------
# Step 4: UI Component Reuse Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 4/6: UI Component Reuse Check${NC}"
echo "----------------------------------------"

# Check for generic option dialog
if grep -rq "OptionSelectDialog\|OptionPickerDialog\|CommonSelectDialog\|GenericPickerDialog" "$PROJECT" --include="*.kt" 2>/dev/null; then
    echo -e "  ${PASS} Generic option picker dialog found (reused across fields)"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "  ${WARN} No generic option picker dialog detected"
    echo "  Check: Each KYC field should NOT have its own Dialog class"
    WARN_COUNT=$((WARN_COUNT + 1))
fi

# Check for per-field dialogs (anti-pattern)
PER_FIELD=$(grep -r "EducationDialog\|MaritalDialog\|WorkTypeDialog\|IncomeDialog\|RelationDialog" "$PROJECT" --include="*.kt" -l 2>/dev/null || true)
if [ -n "$PER_FIELD" ]; then
    echo -e "  ${FAIL} Per-field dialogs found (anti-pattern — should use generic dialog):"
    echo "$PER_FIELD" | while read -r f; do echo "       $f"; done
    echo "  Fix: Replace with single OptionSelectDialog(title, options, onSelect)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo -e "  ${PASS} No per-field dialogs detected"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

echo ""

# ---------------------------------------------------------------------------
# Step 5: Retain Dialog Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 5/6: Retain Dialog Check${NC}"
echo "----------------------------------------"

# Check for back press handling in KYC screens
BACK_HANDLERS=$(grep -r "BackHandler\|onBackPressed\|OnBackClick\|retainDialog\|showRetainDialog\|isRetainDialogVisible" "$PROJECT" --include="*.kt" -l 2>/dev/null | grep -i "kyc\|personal\|contact\|identity\|bank\|credit\|face" | head -10 || true)

if [ -n "$BACK_HANDLERS" ]; then
    echo -e "  ${PASS} Back press handling found in KYC screens:"
    echo "$BACK_HANDLERS" | while read -r f; do echo "       $f"; done
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "  ${WARN} No retain dialog / back press handling detected in KYC screens"
    echo "  Fix: Add BackHandler/onBackPressed that shows retain dialog before exit"
    WARN_COUNT=$((WARN_COUNT + 1))
fi

# Check that non-KYC face mode skips retain
echo ""

# ---------------------------------------------------------------------------
# Step 6: Compilation Check
# ---------------------------------------------------------------------------
echo -e "${BOLD}Step 6/6: Compilation Check${NC}"
echo "----------------------------------------"

if [ -f "$PROJECT/gradlew" ]; then
    echo "  Running: ./gradlew compileDebugKotlin..."
    if (cd "$PROJECT" && ./gradlew compileDebugKotlin --no-daemon -q 2>&1); then
        echo -e "  ${PASS} Compilation successful"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "  ${FAIL} Compilation failed — check errors above"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
else
    echo -e "  ${WARN} gradlew not found — skip compilation check"
    echo "  Fix: Run './gradlew compileDebugKotlin' manually to verify"
    WARN_COUNT=$((WARN_COUNT + 1))
fi

echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "======================================================================"
echo -e "  ${BOLD}Verification Summary${NC}"
echo "======================================================================"
echo -e "  ${GREEN}PASS:${NC} $PASS_COUNT"
echo -e "  ${RED}FAIL:${NC} $FAIL_COUNT"
echo -e "  ${YELLOW}WARN:${NC} $WARN_COUNT"
echo ""

if [ "$FAIL_COUNT" -eq 0 ] && [ "$WARN_COUNT" -eq 0 ]; then
    echo -e "  ${GREEN}All checks passed! KYC implementation looks solid.${NC}"
elif [ "$FAIL_COUNT" -eq 0 ]; then
    echo -e "  ${YELLOW}All critical checks passed. Review ${WARN_COUNT} warning(s) above.${NC}"
else
    echo -e "  ${RED}${FAIL_COUNT} critical issue(s) found. Fix FAIL items above first.${NC}"
fi

echo ""
echo "======================================================================"
