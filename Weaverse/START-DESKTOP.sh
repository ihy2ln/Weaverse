#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
if [[ -f Weaverse.jar ]]; then
  exec java -jar Weaverse.jar --data="$DIR/data"
fi
echo "Weaverse.jar not found in $DIR"
exit 1
