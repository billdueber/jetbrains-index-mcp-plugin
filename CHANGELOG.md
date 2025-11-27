<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# jetbrains-index-mcp-plugin Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- MCP server with HTTP+SSE transport on IDE's built-in web server
- 20 MCP tools for navigation, code intelligence, project structure, and refactoring
- 4 MCP resources for querying project state
- Tool window with command history, filtering, and export functionality
- Multi-project support with `project_path` parameter
- Client configuration generator for Claude Code, Claude Desktop, Cursor, VS Code, and Windsurf
- "Install on Coding Agents" button with two sections:
  - **Install Now**: One-click installation for Claude Code CLI (runs command automatically)
  - **Copy Configuration**: Copy JSON config to clipboard for other clients
- Toolbar actions with icons: Refresh, Copy URL, Clear History, Export History
- Settings for history size, auto-scroll, timestamps, and write operation confirmation
