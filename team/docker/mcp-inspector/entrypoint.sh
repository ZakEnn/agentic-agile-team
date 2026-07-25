#!/bin/sh
set -e

# Generate MCP servers config from environment variables
cat > /app/mcp-servers.json <<EOF
{
  "mcpServers": {
    "confluence": {
      "command": "node",
      "args": ["/usr/local/lib/node_modules/@atlassian-dc-mcp/confluence/bin/run.js"],
      "env": {
        "CONFLUENCE_HOST": "${CONFLUENCE_HOST}",
        "CONFLUENCE_API_TOKEN": "${CONFLUENCE_API_TOKEN}"
      }
    },
    "jira": {
      "command": "node",
      "args": ["/usr/local/lib/node_modules/@atlassian-dc-mcp/jira/bin/run.js"],
      "env": {
        "JIRA_HOST": "${JIRA_HOST}",
        "JIRA_API_TOKEN": "${JIRA_API_TOKEN}"
      }
    },
    "gitlab": {
      "command": "node",
      "args": ["/usr/local/lib/node_modules/@zereight/mcp-gitlab/build/index.js"],
      "env": {
        "GITLAB_API_URL": "${GITLAB_API_URL}",
        "GITLAB_PERSONAL_ACCESS_TOKEN": "${GITLAB_API_TOKEN}"
      }
    },
    "sonarqube": {
      "command": "node",
      "args": ["/usr/local/lib/node_modules/mcp-sonarqube/dist/index.js"],
      "env": {
        "SONARQUBE_URL": "${SONARQUBE_URL}",
        "SONARQUBE_TOKEN": "${SONARQUBE_API_TOKEN}"
      }
    }
  }
}
EOF

echo "MCP Inspector starting..."
echo "UI: http://localhost:6274?MCP_PROXY_AUTH_TOKEN=${MCP_PROXY_AUTH_TOKEN}"
echo "Config file: /app/mcp-servers.json"
echo "Available servers: confluence, jira, gitlab, sonarqube"

# If MCP_SERVER is set, connect directly to that server
if [ -n "${MCP_SERVER}" ]; then
  echo "Auto-connecting to server: ${MCP_SERVER}"
  exec mcp-inspector --config /app/mcp-servers.json --server "${MCP_SERVER}" "$@"
else
  # Start in plain UI mode — user picks server in browser
  exec mcp-inspector "$@"
fi
