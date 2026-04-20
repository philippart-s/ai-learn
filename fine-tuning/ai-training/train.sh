#!/bin/bash
set -euo pipefail

# === Required ENV variables ===
: "${HF_TOKEN:?HF_TOKEN is required}"
: "${WANDB_TOKEN:?WANDB_TOKEN is required}"

# === Optional ENV variables (with defaults) ===
: "${CONFIG_FILE:=instruct-lora-3b-devoxx.yml}"
: "${HF_REPO:=wildagsx/Llama-3.2-3B-Instruct-devoxx}"
: "${HF_REVISION:=v0.1}"
: "${OUTPUT_DIR:=/workspace/out/llama-3.2-3b-devoxx}"
: "${INFERENCE_PROMPT:=Quels sont les talks de Stephane Philippart a Devoxx France 2026 ?}"
: "${BASE_MODEL:=meta-llama/Llama-3.2-3B-Instruct}"

MERGED_DIR="${OUTPUT_DIR}/merged"
LOCAL_MODEL_DIR="/workspace/base-model"
S3_MODEL_CACHE="/workspace/s3-cache/base-model"

# Use local disk for HuggingFace model cache (S3 volume too slow for large downloads)
export HF_HOME="/workspace/hf-cache"

# Disable xet transfer protocol — it hangs on large files in AI Training environment.
# Force classic HTTPS download which is more reliable.
export HF_HUB_ENABLE_HF_TRANSFER=0
export HF_HUB_DISABLE_XET=1

# Set WandB run name to match HF model/revision
export WANDB_NAME="${HF_REPO##*/}-${HF_REVISION}"

echo "=== Step 1: Copy dataset and config ==="
mkdir -p /workspace/dataset
cp /workspace/ai-learn/fine-tuning/datasets/out/devoxx-2026-dataset.json /workspace/dataset/
cp /workspace/ai-learn/fine-tuning/notebooks/"$CONFIG_FILE" /workspace/

echo "=== Step 1b: Restore base model from S3 cache if available ==="
if [ -d "$S3_MODEL_CACHE" ]; then
    if find "$S3_MODEL_CACHE" -name "*.incomplete" | grep -q .; then
        echo "WARNING: S3 cache contains incomplete files, ignoring cache and cleaning up..."
        rm -rf "$S3_MODEL_CACHE"
    else
        echo "Model found in S3 cache (complete), copying to local..."
        cp -r "$S3_MODEL_CACHE" "$LOCAL_MODEL_DIR"
        echo "Model restored from S3 cache."
    fi
fi

echo "=== Step 1c: Download base model if not available locally ==="
if [ ! -d "$LOCAL_MODEL_DIR" ]; then
    echo "Downloading $BASE_MODEL from HuggingFace (excluding original/ PyTorch weights)..."
    huggingface-cli download "$BASE_MODEL" \
        --local-dir "$LOCAL_MODEL_DIR" \
        --exclude "original/*"
    echo "Model downloaded to $LOCAL_MODEL_DIR."
else
    echo "Model already available at $LOCAL_MODEL_DIR, skipping download."
fi

echo "=== Step 1d: Patch config to use local model path ==="
sed -i "s|^base_model:.*|base_model: $LOCAL_MODEL_DIR|" /workspace/"$CONFIG_FILE"
echo "Config patched: base_model set to $LOCAL_MODEL_DIR"

echo "=== Step 2: Login to HuggingFace and WandB ==="
huggingface-cli login --token "$HF_TOKEN"
wandb login "$WANDB_TOKEN"

echo "=== Step 3: Preprocess ==="
axolotl preprocess /workspace/"$CONFIG_FILE" --no-download

echo "=== Step 3b: Cache base model to S3 for future runs ==="
if [ ! -d "$S3_MODEL_CACHE" ] && [ -d "$LOCAL_MODEL_DIR" ]; then
    if find "$LOCAL_MODEL_DIR" -name "*.incomplete" | grep -q .; then
        echo "WARNING: Local model has incomplete files, skipping S3 cache."
    else
        echo "Saving model to S3 cache..."
        mkdir -p "$(dirname "$S3_MODEL_CACHE")"
        cp -r "$LOCAL_MODEL_DIR" "$S3_MODEL_CACHE"
        echo "Model cached to S3."
    fi
else
    echo "Model already in S3 cache or not available locally, skipping."
fi

echo "=== Step 4: Train ==="
axolotl train /workspace/"$CONFIG_FILE"

echo "=== Step 5: Inference test ==="
echo "$INFERENCE_PROMPT" | axolotl inference /workspace/"$CONFIG_FILE" \
  --lora-model-dir="$OUTPUT_DIR"

echo "=== Step 6: Merge LoRA ==="
axolotl merge-lora /workspace/"$CONFIG_FILE" \
  --lora-model-dir="$OUTPUT_DIR"

echo "=== Step 7: Push merged model to HuggingFace ==="
huggingface-cli upload "$HF_REPO" "$MERGED_DIR" --revision "$HF_REVISION"

echo "=== DONE ==="
