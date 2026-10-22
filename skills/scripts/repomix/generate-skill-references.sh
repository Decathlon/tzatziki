#!/usr/bin/env bash
#
# generate-skill-references.sh
#
# Regenerates reference docs for both Tzatziki skills.
#
# Usage:
#   ./skills/scripts/repomix/generate-skill-references.sh          # generate all
#   ./skills/scripts/repomix/generate-skill-references.sh core http # generate only specified modules
#
# Prerequisites:
#   - repomix installed (npm install -g repomix)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONFIG_DIR="$SCRIPT_DIR"

ALL_MODULES=(core http spring spring-jpa kafka spring-kafka spring-mongodb opensearch logback mcp jackson3)
REFS_DIR="$PROJECT_ROOT/skills/java-tests-tzatziki/references"
LEGACY_REFS_DIR="$PROJECT_ROOT/skills/add-cucumber-tests/references"

for refs_dir in "$REFS_DIR" "$LEGACY_REFS_DIR"; do
  if [[ ! -d "$refs_dir" ]]; then
    echo "Error: skill reference directory not found: $refs_dir" >&2
    exit 1
  fi
done

# Determine which modules to generate
if [[ $# -gt 0 ]]; then
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
  if [[ ! -f "$config" ]]; then
    echo "⚠  Config not found: $config (skipping)"
    FAILED+=("$module")
    continue
  fi

  echo "→ Generating steps-${module}.md ..."
  if (cd "$PROJECT_ROOT" && repomix -c "$config" > /dev/null 2>&1); then
    # Strip Repomix boilerplate preamble to save ~320 tokens per file.
    # Keep everything from "# User Provided Header" onward.
    outfile="$REFS_DIR/steps-${module}.md"
    if [[ ! -f "$outfile" ]]; then
      echo "  ✗ Repomix completed without creating $outfile"
      FAILED+=("$module")
      continue
    fi
    start_line=$(grep -n "^# User Provided Header" "$outfile" | head -1 | cut -d: -f1)
    if [[ -n "$start_line" ]]; then
      tail -n +"$start_line" "$outfile" > "${outfile}.tmp"
      mv "${outfile}.tmp" "$outfile"
    fi
    cp "$outfile" "$LEGACY_REFS_DIR/steps-${module}.md"
    GENERATED+=("$module")
  else
    echo "  ✗ Failed to generate steps-${module}.md"
    FAILED+=("$module")
  fi
done

# Summary
echo ""
echo "=== Summary ==="

if [[ ${#GENERATED[@]} -gt 0 ]]; then
  echo ""
  echo "Generated ${#GENERATED[@]} module reference(s):"
  for module in "${GENERATED[@]}"; do
    file="$REFS_DIR/steps-${module}.md"
    if [[ -f "$file" ]]; then
      size=$(du -h "$file" | cut -f1 | xargs)
      lines=$(wc -l < "$file" | xargs)
      echo "  ✓ steps-${module}.md  (${lines} lines, ${size})"
    fi
  done
fi

if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo ""
  echo "Failed ${#FAILED[@]} module(s): ${FAILED[*]}"
fi

echo ""
echo "Done."

if [[ ${#FAILED[@]} -gt 0 ]]; then
  exit 1
fi
