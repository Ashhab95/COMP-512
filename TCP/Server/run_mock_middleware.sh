#!/usr/bin/env bash
# Usage: ./run_mock_middleware.sh [<port>]

set -euo pipefail

echo "Edit file run_mock_middleware.sh to include instructions for launching the mock middleware"
echo '  $1 - optional TCP port (default: 5000)'

PORT="${1:-5000}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

javac -d "$SCRIPT_DIR" "$SCRIPT_DIR/Server/TCP/MockMiddleware.java"

java -cp "$SCRIPT_DIR" Server.TCP.MockMiddleware "$PORT"
