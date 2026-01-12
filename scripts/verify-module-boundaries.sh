#!/bin/bash
set -e

echo "=== Verifying Module Boundaries ==="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track failures
FAILURES=0

# Function to check for forbidden imports
check_imports() {
  local module=$1
  shift
  local forbidden_patterns=("$@")
  local found_violations=0

  echo ""
  echo "Checking module: $module"

  for pattern in "${forbidden_patterns[@]}"; do
    if grep -r "$pattern" "modules/$module/src" 2>/dev/null; then
      echo -e "${RED}ERROR: Module $module contains forbidden import: $pattern${NC}"
      found_violations=1
    fi
  done

  if [ $found_violations -eq 0 ]; then
    echo -e "${GREEN}✓ Module $module boundaries verified${NC}"
  else
    FAILURES=$((FAILURES + 1))
  fi

  return $found_violations
}

# Check IR module - must have NO dependencies
echo ""
echo "=== Checking IR Module (Core) ==="
check_imports "ir" \
  "import org.scalameta" \
  "import com.squareup" \
  "import org.springframework" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli"

# Check parser module - depends on IR only
echo ""
echo "=== Checking Parser Module ==="
check_imports "parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli" \
  "import com.squareup.kotlinpoet"

# Check renderer module - depends on IR only
echo ""
echo "=== Checking Renderer Module ==="
check_imports "renderer" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli" \
  "import org.scalameta"

# Check validator module - depends on IR only
echo ""
echo "=== Checking Validator Module ==="
check_imports "validator" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.cli"

# Summary
echo ""
echo "=== Summary ==="
if [ $FAILURES -eq 0 ]; then
  echo -e "${GREEN}✓ All module boundaries verified successfully${NC}"
  exit 0
else
  echo -e "${RED}✗ Found $FAILURES module(s) with boundary violations${NC}"
  echo ""
  echo "Module boundary rules:"
  echo "  - IR: No dependencies on other modules or frameworks"
  echo "  - Parser: Depends only on IR (+ Scalameta)"
  echo "  - Renderer: Depends only on IR (+ KotlinPoet)"
  echo "  - Validator: Depends only on IR (+ Cats)"
  echo "  - CLI: Orchestrates all modules"
  echo ""
  echo "See MODULE_CONTRACTS.md for details."
  exit 1
fi
