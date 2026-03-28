#!/usr/bin/env bash
#
# generate-skill-references.sh
#
# Regenerates all per-module reference docs for the add-cucumber-tests skill.
#
# Usage:
#   ./.github/skills/add-cucumber-tests/repomix/generate-skill-references.sh          # generate all
#   ./.github/skills/add-cucumber-tests/repomix/generate-skill-references.sh core http # generate only specified modules
#
# Prerequisites:
#   - repomix installed (npm install -g repomix)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
CONFIG_DIR="$SCRIPT_DIR"

ALL_MODULES=(core http spring spring-jpa spring-kafka spring-mongodb opensearch logback mcp)
REFS_DIR="$PROJECT_ROOT/.github/skills/add-cucumber-tests/references"

# Determine which modules to generate
if [ $# -gt 0 ]; then
  MODULES=("$@")
else
  MODULES=("${ALL_MODULES[@]}")
fi

# Check repomix is available
if ! command -v repomix &> /dev/null; then
  echo "Error: repomix is not installed. Install with: npm install -g repomix"
  exit 1
fi

echo "=== Tzatziki Skill Reference Generator ==="
echo ""

GENERATED=()
FAILED=()

# Generate per-module references
for module in "${MODULES[@]}"; do
  config="$CONFIG_DIR/repomix.${module}.json"
  if [ ! -f "$config" ]; then
    echo "⚠  Config not found: $config (skipping)"
    FAILED+=("$module")
    continue
  fi

  echo "→ Generating steps-${module}.md ..."
  if (cd "$PROJECT_ROOT" && repomix -c "$config" > /dev/null 2>&1); then
    # Strip Repomix boilerplate preamble to save ~320 tokens per file.
    # Keep everything from "# User Provided Header" onward.
    outfile="$REFS_DIR/steps-${module}.md"
    if [ -f "$outfile" ]; then
      start_line=$(grep -n "^# User Provided Header" "$outfile" | head -1 | cut -d: -f1)
      if [ -n "$start_line" ]; then
        tail -n +"$start_line" "$outfile" > "${outfile}.tmp"
        mv "${outfile}.tmp" "$outfile"
      fi
    fi
    GENERATED+=("$module")
  else
    echo "  ✗ Failed to generate steps-${module}.md"
    FAILED+=("$module")
  fi
done

# Summary
echo ""
echo "=== Summary ==="

if [ ${#GENERATED[@]} -gt 0 ]; then
  echo ""
  echo "Generated ${#GENERATED[@]} module reference(s):"
  for module in "${GENERATED[@]}"; do
    file="$REFS_DIR/steps-${module}.md"
    if [ -f "$file" ]; then
      size=$(du -h "$file" | cut -f1 | xargs)
      lines=$(wc -l < "$file" | xargs)
      echo "  ✓ steps-${module}.md  (${lines} lines, ${size})"
    fi
  done
fi

if [ ${#FAILED[@]} -gt 0 ]; then
  echo ""
  echo "Failed ${#FAILED[@]} module(s): ${FAILED[*]}"
fi

echo ""
echo "Done."
