#!/usr/bin/env bash
#
# build-and-install.sh
#
# Builds the jbimcp plugin, installs it into the running IDE via MCP,
# and restarts the IDE.
#
# Reads the MCP server URL from .pi/mcp.json using the server name
# given as argument (default: intellij-index).
#
# Usage:
#   ./build-and-install.sh [server-name]
#
# Example:
#   ./build-and-install.sh
#   ./build-and-install.sh intellij-index

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || realpath "$SCRIPT_DIR")"
MCP_CONFIG="${PROJECT_ROOT}/.pi/mcp.json"
SERVER_NAME="${1:-intellij-index}"

# ── Step 1: Read MCP URL from config ────────────────────────────────────────

if [[ ! -f "$MCP_CONFIG" ]]; then
  echo "ERROR: MCP config not found at $MCP_CONFIG"
  exit 1
fi

MCP_URL="$(jq -r --arg name "$SERVER_NAME" '.mcpServers[$name].url // empty' "$MCP_CONFIG")"
if [[ -z "$MCP_URL" ]]; then
  echo "ERROR: Server '$SERVER_NAME' not found in $MCP_CONFIG"
  echo "Available servers:"
  jq -r '.mcpServers | keys[]' "$MCP_CONFIG"
  exit 1
fi

echo "==> Using MCP server: $SERVER_NAME at $MCP_URL"

# ── Step 2: Build the plugin ────────────────────────────────────────────────

echo ""
echo "==> Building plugin..."
cd "$PROJECT_ROOT"

JAVA_HOME="${JAVA_HOME:-$(mise where java@17 2>/dev/null || echo '')}"
if [[ -z "$JAVA_HOME" ]]; then
  echo "JAVA_HOME not set and 'mise where java@17' failed. Set JAVA_HOME or 'mise use java@17'."
  exit 1
fi

export JAVA_HOME

if ! ./gradlew buildPlugin 2>&1; then
  echo "ERROR: buildPlugin failed"
  exit 1
fi

# ── Step 3: Find the built zip ───────────────────────────────────────────────

ZIP_FILE="$(ls -t "$PROJECT_ROOT/build/distributions/"*.zip 2>/dev/null | head -1)"
if [[ -z "$ZIP_FILE" ]]; then
  echo "ERROR: No zip file found in build/distributions/"
  exit 1
fi

echo "==> Plugin zip: $ZIP_FILE"

# ── Step 4: Install plugin via MCP ──────────────────────────────────────────

echo ""
echo "==> Installing plugin..."

JSON_RPC_INSTALL=$(
  cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "ide_install_plugin",
    "arguments": {
      "path": "$ZIP_FILE",
      "project_path": "$PROJECT_ROOT"

    }
  }
}
EOF
)

INSTALL_RESPONSE="$(curl -s -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -d "$JSON_RPC_INSTALL")"

echo "Install response: $INSTALL_RESPONSE"

# Check for error
if echo "$INSTALL_RESPONSE" | jq -e '.error != null' >/dev/null 2>&1; then
  echo "ERROR: Plugin install failed: $(echo "$INSTALL_RESPONSE" | jq -r '.error.message')"
  exit 1
fi

# ── Step 5: Restart IDE via MCP ──────────────────────────────────────────────

echo ""
echo "==> Restarting IDE..."

JSON_RPC_RESTART=$(
  cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "ide_restart",
    "arguments": {
      "project_path": "$PROJECT_ROOT"
    }
  }
}
EOF
)

# Restart may kill the server before returning — ignore curl exit code
curl -s -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -d "$JSON_RPC_RESTART" \
  --max-time 5 || true

echo ""
echo "==> IDE restart initiated. If the IDE does not come back, restart it manually."