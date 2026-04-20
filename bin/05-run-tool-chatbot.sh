#!/bin/bash
# Run the Tool chatbot using RAG + function calling (date/time tools)
clear

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bat -P -r 16: "$SCRIPT_DIR/$(basename "$0")"

source "$SCRIPT_DIR/../.env"
source "$SCRIPT_DIR/model-select.sh"
select_model

echo ""
echo "Run JBang script using model $OVH_AI_ENDPOINTS_MODEL_NAME"
echo "Embedding model: $OVH_AI_ENDPOINTS_EMBEDDING_MODEL_NAME"

cd "$SCRIPT_DIR/../chatbot"
jbang --quiet _05_ToolChatbot.java
