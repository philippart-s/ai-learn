# 🛠️ Load environment variables 🛠️
source ../.env

clear

bat -P -r 12: $(basename "$0")

read -n 1 -p "Press any key to continue"

echo ""

ovhai app run --name $1-$2 \
	--flavor l4-1-gpu \
	--gpu 1 \
	--default-http-port 8000 \
	--env OUTLINES_CACHE_DIR=/tmp/.outlines \
	--env HF_TOKEN=$MY_HF_TOKEN \
	--env HF_HOME=/hub \
	--env HF_DATASETS_TRUST_REMOTE_CODE=1 \
	--env HF_HUB_ENABLE_HF_TRANSFER=0 \
	--volume standalone:/hub:RW \
	--volume standalone:/workspace:RW \
	vllm/vllm-openai:v0.8.2 \
	-- bash	-c "vllm serve wildagsx/$1 --revision $2 --max-model-len 65536"

