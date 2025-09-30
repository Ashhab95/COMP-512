#!/usr/bin/env bash
# Usage: ./run_server.sh <name> <port>
# Example: ./run_server.sh Flights 5001

set -euo pipefail

# Always run from the directory this script lives in
SCRIPT_DIR="$(cd -- "$(dirname "$0")" >/dev/null 2>&1 && pwd)"
cd "$SCRIPT_DIR"

javac -d . Server/Common/*.java Server/Interface/*.java Server/TCP/TCPResourceManager.java

echo "Launching TCP Resource Manager"
echo "  $1 - resource manager name (Flights, Cars, Rooms, ...)"
echo "  $2 - port to listen on"

exec java Server.TCP.TCPResourceManager "$1" "$2"
