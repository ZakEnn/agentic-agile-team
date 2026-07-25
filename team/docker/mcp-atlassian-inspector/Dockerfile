FROM node:22-slim

LABEL maintainer="agile-team"
LABEL description="MCP Inspector with sooperset/mcp-atlassian (Confluence DC support via PAT)"

# Install MCP Inspector globally
RUN npm install -g @modelcontextprotocol/inspector@latest \
    && npm cache clean --force

# Install Python/pip for uvx (mcp-atlassian is a Python package)
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv pipx \
    && rm -rf /var/lib/apt/lists/*

# Install mcp-atlassian via pipx (isolated Python environment)
RUN pipx install mcp-atlassian

# Ensure pipx bin directory is on PATH
ENV PATH="/root/.local/bin:$PATH"

# Copy entrypoint
COPY entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

WORKDIR /app

# Required for Docker: bind to all interfaces
ENV HOST=0.0.0.0
ENV MCP_AUTO_OPEN_ENABLED=false
ENV MCP_PROXY_AUTH_TOKEN=mcp-inspector-token

# Confluence DC defaults
ENV CONFLUENCE_URL=https://espace.agir.orange.com
ENV CONFLUENCE_SSL_VERIFY=true
ENV READ_ONLY_MODE=true

# MCP Inspector ports
EXPOSE ${CLIENT_PORT:-6314} ${SERVER_PORT:-6317}

ENTRYPOINT ["/app/entrypoint.sh"]
