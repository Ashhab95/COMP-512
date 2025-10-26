#!/usr/bin/env bash
# Usage:
#   ./run_middleware.sh <listen_port> <flightHost:port> <carHost:port> <roomHost:port>
# Example:
#   ./run_middleware.sh 5000 localhost:5101 localhost:5102 localhost:5103

set -euo pipefail

LISTEN="${1:-5000}"
FLIGHT="${2:-localhost:5101}"
CAR="${3:-localhost:5102}"
ROOM="${4:-localhost:5103}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

javac -d "$SCRIPT_DIR" \
  $(find "$SCRIPT_DIR/Server/Common" -name "*.java") \
  $(find "$SCRIPT_DIR/Server/Middleware" -name "*.java") \
  $(find "$SCRIPT_DIR/Server/TCP" -name "TCPMiddleware.java")

echo "[run_middleware] listening on :$LISTEN"
echo "[run_middleware] RMs: Flights=$FLIGHT  Cars=$CAR  Rooms=$ROOM"

exec java -cp "$SCRIPT_DIR" Server.TCP.TCPMiddleware "$LISTEN" "$FLIGHT" "$CAR" "$ROOM"
