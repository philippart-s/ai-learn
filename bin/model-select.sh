#!/bin/bash

# Shared model selection function for chatbot demos.
# Source this file, then call select_model to display the menu
# and override OVH_AI_ENDPOINTS_* env vars based on the user's choice.
#
# Usage in a script:
#   source "$(dirname "${BASH_SOURCE[0]}")/model-select.sh"
#   select_model

select_model() {
  echo ""
  echo "Select model source:"
  echo "  1. ☁️  AI Endpoints (default)"
  echo "  2. 🤗  HuggingFace AI Deploy"
  echo "  3. 🔬  Fine-tuned AI Deploy"
  echo ""
  read -r -p "Your choice [1]: " MODEL_CHOICE
  MODEL_CHOICE="${MODEL_CHOICE:-1}"

  case "$MODEL_CHOICE" in
    1)
      MODEL_SOURCE="AI Endpoints"
      echo ""
      echo "Select AI Endpoints model:"
      echo "  0. 🔒  Keep default from .env ($OVH_AI_ENDPOINTS_MODEL_NAME)"
      echo "  1. gpt-oss-120b"
      echo "  2. Llama-3.1-8B-Instruct"
      echo "  3. Mistral-7B-Instruct-v0.3"
      echo "  4. Mistral-Nemo-Instruct-2407"
      echo ""
      read -r -p "Your choice [0]: " AI_MODEL_CHOICE
      AI_MODEL_CHOICE="${AI_MODEL_CHOICE:-0}"

      case "$AI_MODEL_CHOICE" in
        0) ;; # keep .env value
        1) export OVH_AI_ENDPOINTS_MODEL_NAME="gpt-oss-120b" ;;
        2) export OVH_AI_ENDPOINTS_MODEL_NAME="Llama-3.1-8B-Instruct" ;;
        3) export OVH_AI_ENDPOINTS_MODEL_NAME="Mistral-7B-Instruct-v0.3" ;;
        4) export OVH_AI_ENDPOINTS_MODEL_NAME="Mistral-Nemo-Instruct-2407" ;;
        *)
          echo "Invalid model choice: $AI_MODEL_CHOICE"
          exit 1
          ;;
      esac
      ;;
    2)
      MODEL_SOURCE="HuggingFace AI Deploy"
      export OVH_AI_ENDPOINTS_ACCESS_TOKEN="${HF_DEPLOY_ACCESS_TOKEN:?HF_DEPLOY_ACCESS_TOKEN is not set in .env}"
      export OVH_AI_ENDPOINTS_MODEL_NAME="${HF_DEPLOY_MODEL_NAME:?HF_DEPLOY_MODEL_NAME is not set in .env}"
      export OVH_AI_ENDPOINTS_MODEL_URL="${HF_DEPLOY_MODEL_URL:?HF_DEPLOY_MODEL_URL is not set in .env}"
      ;;
    3)
      MODEL_SOURCE="Fine-tuned AI Deploy"
      export OVH_AI_ENDPOINTS_ACCESS_TOKEN="${FINETUNED_ACCESS_TOKEN:?FINETUNED_ACCESS_TOKEN is not set in .env}"
      export OVH_AI_ENDPOINTS_MODEL_NAME="${FINETUNED_MODEL_NAME:?FINETUNED_MODEL_NAME is not set in .env}"
      export OVH_AI_ENDPOINTS_MODEL_URL="${FINETUNED_MODEL_URL:?FINETUNED_MODEL_URL is not set in .env}"
      ;;
    *)
      echo "Invalid choice: $MODEL_CHOICE"
      exit 1
      ;;
  esac

  # Display selected model info (NEVER show the API key)
  echo ""
  echo "Using: $MODEL_SOURCE"
  echo "Model: $OVH_AI_ENDPOINTS_MODEL_NAME"
  echo "URL:   $OVH_AI_ENDPOINTS_MODEL_URL"
}
