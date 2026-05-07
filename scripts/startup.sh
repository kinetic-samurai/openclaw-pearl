#!/usr/bin/env bash

# Colors for the ride
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Ensure we are in the project root
cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

echo -e "${CYAN}🌊 Riding the waves of digital love...${NC}"

# Check for .env
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️  .env not found. Creating from example...${NC}"
    if [ -f env.example ]; then
        cp env.example .env
    else
        echo -e "${YELLOW}❌ env.example not found. Please create .env manually.${NC}"
        exit 1
    fi
fi

source .env

# Kill existing processes and stale log watchers
pkill -f "turbo_serve.py" || true
pkill -f "litert-bridge" || true
pkill -f "tail -f turbo_serve.log" || true
pkill -f "tail -f bridge.log" || true

# Pre-create log files to avoid tail errors
touch turbo_serve.log bridge.log
# Clear logs for a fresh ride
> turbo_serve.log
> bridge.log

echo -e "${GREEN}🚀 Starting LiteRT-LM Server...${NC}"
python3 -u apps/litert-bridge/turbo_serve.py --backend cpu --port 9379 > turbo_serve.log 2>&1 &
LITERT_PID=$!

echo -e "${GREEN}🚀 Starting Kotlin Bridge...${NC}"
./scripts/run-bridge.sh > bridge.log 2>&1 &
BRIDGE_PID=$!

echo -e "${CYAN}👀 Watching logs (Ctrl+C to stop)...${NC}"

# Cleanup on exit: Kill the entire process group
trap 'trap - INT TERM; kill 0; echo -e "\n${YELLOW}🛑 Services stopped. See ya!${NC}"; exit' INT TERM

# Interleave logs with colors
tail -f turbo_serve.log | sed "s/^/${CYAN}[LiteRT] ${NC}/" &
tail -f bridge.log | sed "s/^/${GREEN}[Bridge] ${NC}/" &

wait
