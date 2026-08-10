#!/usr/bin/env bash
# Guards LEARNINGS.md structure: lesson headings are '## §N. Title' with
# unique, never-reused numeric ids, and every lesson id appears in the
# 'Key takeaways' section. Mirrors the web repo's learnings-format test.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT/LEARNINGS.md"

fail() {
  echo "LEARNINGS.md format check failed: $*" >&2
  exit 1
}

[[ -f "$DOC" ]] || fail "missing $DOC"

numbers="$(grep -E '^## §[0-9]+\. ' "$DOC" | sed -E 's/^## §([0-9]+)\..*/\1/')"
[[ -n "$numbers" ]] || fail "expected at least one '## §N. Title' heading"

dupes="$(printf '%s\n' "$numbers" | sort -n | uniq -d)"
[[ -z "$dupes" ]] || fail "lesson number(s) reused: $(printf '%s ' $dupes)- numbers are stable ids and must never be reused"

while IFS= read -r heading; do
  if ! printf '%s' "$heading" | grep -qE '^## (§[0-9]+\. \S|Key takeaways$)'; then
    fail "unexpected heading '$heading' — lessons must use '## §N. Title' so their ids stay citable"
  fi
done < <(grep -E '^## ' "$DOC")

takeaways="$(sed -n '/^## Key takeaways$/,$p' "$DOC")"
[[ -n "$takeaways" ]] || fail "expected a '## Key takeaways' section"

for n in $numbers; do
  # Boundary after the number: without it, §1 would be satisfied by §10.
  printf '%s\n' "$takeaways" | grep -qE "§${n}([^0-9]|\$)" || fail "lesson §$n is missing from Key takeaways"
done

echo "LEARNINGS.md format check passed."
