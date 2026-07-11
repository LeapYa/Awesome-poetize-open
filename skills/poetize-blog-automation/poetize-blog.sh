#!/usr/bin/env bash
# Thin wrapper for the Poetize blog-automation CLI.
#
# Usage:  poetize-blog <command> [subcommand] [args...]
# This just delegates to scripts/poetize_cli.py, resolving the script path
# relative to this wrapper so it works from any CWD. Python is still required
# at runtime; the wrapper only removes the need to type `python`/`python3`,
# the `scripts/` directory, and the `.py` extension on every call.
#
# NOTE: this is deliberately named `poetize-blog` (not `poetize`) to avoid
# colliding with the system-level `poetize` management command that ships with
# the awesome-poetize-open project root.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="$SCRIPT_DIR/scripts/poetize_cli.py"

if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "error: python3 or python is required to run the Poetize CLI" >&2
  exit 127
fi

exec "$PY" "$CLI" "$@"
