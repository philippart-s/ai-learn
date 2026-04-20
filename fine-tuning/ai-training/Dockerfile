FROM --platform=linux/x86_64 nvidia/cuda:12.6.3-devel-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install Python 3.11 + git
RUN apt-get update && apt-get install -y \
    software-properties-common git \
    && add-apt-repository ppa:deadsnakes/ppa \
    && apt-get update \
    && apt-get install -y python3.11 python3.11-dev python3.11-venv \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/bin/python3.11 /usr/bin/python3 \
    && ln -sf /usr/bin/python3.11 /usr/bin/python

# Install pip
RUN python3 -m ensurepip --upgrade && python3 -m pip install --upgrade pip

WORKDIR /workspace

# Install PyTorch with CUDA 12.6 support
RUN pip install --no-cache-dir torch==2.6.0 --index-url https://download.pytorch.org/whl/cu126

# Install build dependencies for flash-attn (--no-build-isolation requires all deps pre-installed)
RUN pip install --no-cache-dir numpy psutil packaging setuptools wheel

# Install flash-attn from source (needs --no-build-isolation to find torch)
RUN pip install --no-cache-dir --no-build-isolation flash-attn==2.8.2

# Install remaining requirements (axolotl with deepspeed, huggingface_hub, wandb)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy entrypoint script
COPY train.sh .
RUN chmod +x train.sh

# OVHcloud rootless requirement
RUN chown -R 42420:42420 /workspace

CMD ["bash", "/workspace/train.sh"]
