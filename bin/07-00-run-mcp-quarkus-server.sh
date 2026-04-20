# 💬 Run the Quarkus MCP server (HTTP transport) 🤖
clear

bat -P -r 16: $(basename "$0")

# 🛠️ Load environment variables 🛠️
source ../.env

echo ""
echo "🚀 Starting Quarkus MCP Server on http://localhost:8080/mcp"

# 🚀 Run JBang script 🚀
cd ../chatbot
jbang _07_01_DevoxxMcpQuarkusServer.java
