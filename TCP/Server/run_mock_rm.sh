#!/usr/bin/env bash
# Usage:
#   ./run_mock_rm.sh <Flights|Cars|Rooms> [port]
# Examples:
#   ./run_mock_rm.sh Flights 5101
#   ./run_mock_rm.sh Cars    5102
#   ./run_mock_rm.sh Rooms   5103

set -euo pipefail

TYPE="${1:?Expected type: Flights|Cars|Rooms}"
PORT="${2:-5101}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

javac -d "$SCRIPT_DIR" "$SCRIPT_DIR/Server/TCP/MockRM.java"

echo "[run_mock_rm] starting $TYPE on :$PORT"
exec java -cp "$SCRIPT_DIR" Server.TCP.MockRM "$TYPE" "$PORT"
