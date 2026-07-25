FROM node:22-slim

LABEL maintainer="agile-team"
LABEL description="MCP Inspector with pre-installed MCP servers (Confluence, Jira, GitLab, SonarQube)"

# Install MCP Inspector and all MCP server packages globally
RUN npm install -g \
    @modelcontextprotocol/inspector@latest \
    @atlassian-dc-mcp/confluence@latest \
    @atlassian-dc-mcp/jira@latest \
    @zereight/mcp-gitlab@latest \
    mcp-sonarqube@latest \
    && npm cache clean --force

# Copy entrypoint that generates config from env vars at runtime
COPY entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

WORKDIR /app

# Required for Docker: bind to all interfaces
ENV HOST=0.0.0.0
# Disable auto-open browser (headless environment)
ENV MCP_AUTO_OPEN_ENABLED=false
# Predictable auth token (override in docker-compose or .env)
ENV MCP_PROXY_AUTH_TOKEN=mcp-inspector-token

# Default values for MCP server connections
ENV CONFLUENCE_HOST=espace.agir.orange.com
ENV JIRA_HOST=portail.agir.orange.com
ENV GITLAB_API_URL=https://gitlab.tech.orange/api/v4
ENV SONARQUBE_URL=https://sqaas.dos.tech.orange

# UI port and proxy port (configurable via CLIENT_PORT and SERVER_PORT env vars)
EXPOSE ${CLIENT_PORT:-6274} ${SERVER_PORT:-6277}

ENTRYPOINT ["/app/entrypoint.sh"]
