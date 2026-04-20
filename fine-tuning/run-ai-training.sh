# Load environment variables
source ../.env

clear

bat -P -r 16: $(basename "$0")

read -n 1 -p "Press any key to continue"

echo ""

ovhai job run \
  --name Llama-3.2-3B-Instruct-devoxx-$1 \
  --flavor l4-1-gpu \
  --gpu 1 \
  --env HF_TOKEN=$MY_HF_TOKEN \
  --env WANDB_TOKEN=$MY_WANDB_TOKEN \
  --env HF_REPO=wildagsx/Llama-3.2-3B-Instruct-devoxx \
  --env HF_REVISION=$1 \
  --env CONFIG_FILE=instruct-lora-3b-devoxx.yml \
  --env OUTPUT_DIR=/workspace/out/llama-3.2-3b-devoxx \
  --env BASE_MODEL=meta-llama/Llama-3.2-3B-Instruct \
  --env PYTHONUNBUFFERED=1 \
  --unsecure-http \
  --volume fine-tune-devoxx@gh-philippart/:/workspace/ai-learn:RW \
  --volume devoxx-data@S3GRA:/workspace/s3-cache:RW \
  95y036e0.gra7.container-registry.ovh.net/devoxx/axolotl-training:1.1.4
