# 🛠️ Load environment variables 🛠️
source ../.env

clear

bat -P -r 18: $(basename "$0")

read -n 1 -p "Press any key to continue"

echo ""

if [ -z "$1" ]; then
  nb_name=""
else
  nb_name="$1-"
fi

ovhai notebook run conda jupyterlab \
	--name "$nb_name"axolto-llm-fine-tune \
	--framework-version 25.7.0-py311-cudadevel128-gpu \
	--flavor l4-1-gpu \
	--gpu 1 \
	--envvar HF_TOKEN=$MY_HF_TOKEN \
	--envvar WANDB_TOKEN=$MY_WANDB_TOKEN \
	--unsecure-http
