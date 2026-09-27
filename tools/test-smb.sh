#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_PYTHON="${ALBUM_TEST_PYTHON:-python3}"
TEST_SHARE=$(mktemp -d)
"$TEST_PYTHON" tools/smb-test-server.py "$TEST_SHARE" > tools/smb-server.log 2>&1 &
TEST_SERVER_PID=$!
cleanup() { kill "$TEST_SERVER_PID" 2>/dev/null || true; wait "$TEST_SERVER_PID" 2>/dev/null || true; rm -rf "$TEST_SHARE"; }
trap cleanup EXIT
"$TEST_PYTHON" - <<'PY'
import socket, time
for _ in range(100):
    try:
        with socket.create_connection(('127.0.0.1',1445), timeout=.1): break
    except OSError: time.sleep(.1)
else: raise SystemExit('Local SMB fixture did not start')
PY
ALBUM_SMB_TEST=1 ./gradlew :app:testDebugUnitTest > tools/smb-test.log 2>&1
