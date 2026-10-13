#!/bin/bash
# Compile-check all JBang scripts without running them.
# Uses "jbang build --fresh" to resolve dependencies and compile each script.
# Exits with non-zero status if any build fails.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

SCRIPTS=(
  # Chatbot scripts (numbered progression)
  "chatbot/_01_SimpleStreamingChatbot.java"
  "chatbot/_02_SystemStreamingChatbot.java"
  "chatbot/_03_00_MemoryChatbot.java"
  "chatbot/_03_01_MemoryChatbotWithId.java"
  "chatbot/_04_RagChatbot.java"
  "chatbot/_05_ToolChatbot.java"
  "chatbot/_06_00_McpChatbot.java"
  "chatbot/_06_01_BearConfMcpServer.java"
  "chatbot/_07_00_McpHttpChatbot.java"
  "chatbot/_07_01_BearConfMcpQuarkusServer.java"
  "chatbot/_08_AgenticChatbot.java"
  "chatbot/_09_SupervisorChatbot.java"
  "chatbot/_10_GuardRailsChatbot.java"
  "chatbot/_11_SkillChatbot.java"
  "chatbot/_12_WebSearchChatbot.java"
  # Standalone scripts
  "chatbot/ValkeyExplorer.java"
  "fine-tuning/datasets/BearConfDatasetCreation.java"
)

TOTAL=${#SCRIPTS[@]}
PASSED=0
FAILED=0
FAILED_SCRIPTS=()

echo ""
echo "========================================="
echo " JBang Build Check - $TOTAL scripts"
echo "========================================="
echo ""

for script in "${SCRIPTS[@]}"; do
  FULL_PATH="$PROJECT_DIR/$script"

  if [ ! -f "$FULL_PATH" ]; then
    echo -e "  ${YELLOW}SKIP${RESET}  $script (file not found)"
    continue
  fi

  printf "  %-55s " "$script"

  OUTPUT=$(jbang build --fresh "$FULL_PATH" 2>&1)
  EXIT_CODE=$?

  if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}OK${RESET}"
    PASSED=$((PASSED + 1))
  else
    echo -e "${RED}FAIL${RESET}"
    FAILED=$((FAILED + 1))
    FAILED_SCRIPTS+=("$script")
    echo ""
    echo "$OUTPUT" | sed 's/^/    /'
    echo ""
  fi
done

echo ""
echo "========================================="
echo " Results: $PASSED passed, $FAILED failed (out of $TOTAL)"
echo "========================================="

if [ $FAILED -gt 0 ]; then
  echo ""
  echo -e "${RED}Failed scripts:${RESET}"
  for s in "${FAILED_SCRIPTS[@]}"; do
    echo "  - $s"
  done
  echo ""
  exit 1
fi

echo ""
echo -e "${GREEN}All scripts compiled successfully.${RESET}"
echo ""
exit 0
