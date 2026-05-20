#!/usr/bin/env bash
# 停止所有 MakeMovie 服务

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "停止前后端进程..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

echo "停止 Docker 服务..."
if [ -f "$PROJECT_DIR/docker-compose.yml" ]; then
    docker compose -f "$PROJECT_DIR/docker-compose.yml" down 2>/dev/null || true
fi

echo "已全部停止"
