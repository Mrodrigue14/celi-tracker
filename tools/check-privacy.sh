#!/usr/bin/env bash
# Fails if a personal value appears in a file tracked by git.
#
# The repository is public. Its fixtures are synthetic, but real values have
# been pasted back in from a working document more than once. This script makes
# the check mechanical.
#
# The patterns themselves stay OUT of the repository: writing them here would
# publish them, which is exactly the problem being prevented. They live in
# local-data/private-patterns.txt, a gitignored file with one extended regular
# expression per line. Without that file the script checks nothing and says so.
set -euo pipefail

PATTERNS="local-data/private-patterns.txt"
# Name used before the codebase moved to English; still read so an existing
# local file keeps working without being renamed.
LEGACY_PATTERNS="local-data/motifs-prives.txt"
[ ! -f "$PATTERNS" ] && [ -f "$LEGACY_PATTERNS" ] && PATTERNS="$LEGACY_PATTERNS"

if [ ! -f "$PATTERNS" ]; then
    echo "check-privacy: $PATTERNS missing, nothing checked."
    echo "  That file holds the private patterns, one per line. It is gitignored."
    exit 0
fi

found=0
while IFS= read -r pattern; do
    [ -z "$pattern" ] && continue
    case "$pattern" in \#*) continue ;; esac
    if matches=$(git grep -nE "$pattern" -- ':!local-data' ':!tools/check-privacy.sh' 2>/dev/null); then
        echo "PRIVATE PATTERN FOUND: $pattern"
        echo "$matches" | sed 's/^/  /'
        found=1
    fi
done < "$PATTERNS"

if [ "$found" -eq 1 ]; then
    echo
    echo "Personal values are present in files tracked by git."
    echo "The repository is public. Replace them with synthetic values."
    exit 1
fi

echo "check-privacy: no private pattern in tracked files."
