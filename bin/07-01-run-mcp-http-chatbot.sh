#!/bin/bash
# Run the MCP HTTP chatbot (connects to Quarkus MCP server)
clear

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bat -P -r 16: "$SCRIPT_DIR/$(basename "$0")"

source "$SCRIPT_DIR/../.env"
source "$SCRIPT_DIR/model-select.sh"
select_model

echo ""
echo "Run JBang script using model $OVH_AI_ENDPOINTS_MODEL_NAME"
echo "Connecting to MCP server at http://localhost:8080/mcp"

cd "$SCRIPT_DIR/../chatbot"
jbang --quiet _07_00_McpHttpChatbot.java
