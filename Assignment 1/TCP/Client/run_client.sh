#!/usr/bin/env bash
# Usage: ./run_client.sh [<server_hostname> [<server_port>]]

set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

find "$SCRIPT_DIR" -name "*.class" -delete

javac -d "$SCRIPT_DIR" $(find "$SCRIPT_DIR" -name "*.java")

exec java -cp "$SCRIPT_DIR" Client.TCPClient ${1:-localhost} ${2:-5000}
