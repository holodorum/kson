FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

# Core system dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    git \
    gnupg \
    libncurses-dev \
    lsb-release \
    software-properties-common \
    sudo \
    unzip \
    wget \
    zip \
    # Python build deps
    python3 \
    python3-pip \
    python3-venv \
    python3-dev \
    libffi-dev \
    # Rust build deps (bindgen needs clang/llvm)
    clang \
    libclang-dev \
    llvm \
    # Chrome for Kotlin/JS browser tests
    chromium-browser \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js LTS (for LSP tooling)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install uv (Python package manager used by lib-python)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

# Install Rust
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain 1.89.0
ENV PATH="/root/.cargo/bin:${PATH}"

# Install Tailscale
RUN curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.noarmor.gpg | tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null \
    && curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.tailscale-keyring.list | tee /etc/apt/sources.list.d/tailscale.list \
    && apt-get update \
    && apt-get install -y tailscale \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
ARG USERNAME=intellij
ARG USER_UID=1001
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

USER $USERNAME
ENV PATH="/home/${USERNAME}/.cargo/bin:${PATH}"

# Install Rust for the non-root user too
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain 1.89.0

WORKDIR /workspace
