# 🗄️ Explore the Valkey instance used by the memory chatbot 🔍
clear

bat -P -r 16: $(basename "$0")

# 🛠️ Load environment variables 🛠️
source ../.env

echo ""
echo "🚀 Connecting to Valkey at $VALKEY_HOST:$VALKEY_PORT"

# 🚀 Run JBang script 🚀
cd ../chatbot
jbang ValkeyExplorer.java
