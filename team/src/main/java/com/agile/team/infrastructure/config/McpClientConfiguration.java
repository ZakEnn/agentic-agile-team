package com.agile.team.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for MCP (Model Context Protocol) clients.
 * MCP server connections are configured via application.yaml under spring.ai.mcp.client.stdio.connections.
 * Each connection spawns an MCP server subprocess (via npx) with environment-based tokens.
 *
 * Configured servers: confluence, jira, gitlab, sonarqube.
 */
@Configuration
public class McpClientConfiguration {
    // MCP stdio auto-configuration is handled by spring-ai-starter-mcp-client
    // Connections defined in application.yaml → spring.ai.mcp.client.stdio.connections
}
