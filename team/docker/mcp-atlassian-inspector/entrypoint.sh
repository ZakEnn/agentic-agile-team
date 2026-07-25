#!/bin/sh
set -e

# Generate MCP servers config — mcp-atlassian in stdio mode
cat > /app/mcp-servers.json <<EOF
{
  "mcpServers": {
    "mcp-atlassian": {
      "command": "mcp-atlassian",
      "args": [],
      "env": {
        "CONFLUENCE_URL": "${CONFLUENCE_URL}",
        "CONFLUENCE_PERSONAL_TOKEN": "${CONFLUENCE_PERSONAL_TOKEN}",
        "CONFLUENCE_SSL_VERIFY": "${CONFLUENCE_SSL_VERIFY}",
        "READ_ONLY_MODE": "${READ_ONLY_MODE}",
        "CONFLUENCE_SPACES_FILTER": "${CONFLUENCE_SPACES_FILTER}"
      }
    }
  }
}
EOF

echo "=== MCP Atlassian Inspector ==="
echo "UI: http://localhost:${CLIENT_PORT:-6314}?MCP_PROXY_AUTH_TOKEN=${MCP_PROXY_AUTH_TOKEN}"
echo "Confluence URL: ${CONFLUENCE_URL}"
echo "Read-only mode: ${READ_ONLY_MODE}"
echo "SSL verify: ${CONFLUENCE_SSL_VERIFY}"
echo "Spaces filter: ${CONFLUENCE_SPACES_FILTER:-all}"
echo "================================"

exec mcp-inspector --config /app/mcp-servers.json --server mcp-atlassian "$@"
