#!/usr/bin/env bash
set -e

# ============================================================
# MakeMovie 项目启动脚本
# ============================================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"

# --- 颜色输出 ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; }
step() { echo -e "\n${BLUE}━━━ $1 ━━━${NC}"; }

# --- 清理函数 ---
cleanup() {
    echo ""
    log "正在关闭所有服务..."
    kill $BACKEND_PID 2>/dev/null || true
    kill $FRONTEND_PID 2>/dev/null || true
    log "已退出"
}
trap cleanup EXIT INT TERM

# ============================================================
# Step 1: 检查前置依赖
# ============================================================
step "1/5 检查前置依赖"

# Java 21 — 强制使用，不沿用系统默认的 JAVA_HOME
JAVA21_HOME="/opt/homebrew/opt/openjdk@21"
if [ -x "$JAVA21_HOME/bin/java" ]; then
    export JAVA_HOME="$JAVA21_HOME"
    JAVA_VER=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)
    log "Java:  $JAVA_VER"
else
    err "未找到 Java 21，请安装 openjdk@21"
    err "  brew install openjdk@21"
    exit 1
fi

# Node.js
if command -v node &>/dev/null; then
    log "Node:  $(node -v)"
else
    err "未找到 Node.js，请先安装"
    exit 1
fi

# FFmpeg
if command -v ffmpeg &>/dev/null; then
    log "FFmpeg: $(ffmpeg -version 2>&1 | head -1 | cut -d' ' -f3)"
else
    warn "FFmpeg 未安装（视频处理需要）"
    warn "  brew install ffmpeg"
fi

# edge-tts (Python package, for TTS)
if command -v edge-tts &>/dev/null; then
    log "edge-tts: 已安装"
else
    warn "edge-tts 未安装（TTS语音合成需要）"
    warn "  pip install edge-tts"
fi

# ============================================================
# Step 2: 加载环境变量
# ============================================================
step "2/5 加载环境变量"

# Load .env file if it exists
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    source "$PROJECT_DIR/.env"
    set +a
    log ".env 已加载"
else
    warn ".env 文件不存在，跳过"
fi

# LLM 配置
if [ -z "$LLM_API_KEY" ]; then
    warn "LLM_API_KEY 未设置（必需！）"
    warn "  请在 .env 文件中配置 LLM_API_KEY"
fi

# 确保 Homebrew 路径在 PATH 中
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/postgresql@15/bin:$JAVA_HOME/bin:$PATH"

# ============================================================
# Step 3: 启动基础服务 (PostgreSQL + MinIO)
# ============================================================
step "3/5 启动基础服务"

# --- PostgreSQL ---
if command -v psql &>/dev/null && brew services list 2>/dev/null | grep postgresql@15 | grep started >/dev/null; then
    log "PostgreSQL: 已在运行 (Homebrew)"
else
    if command -v docker &>/dev/null; then
        docker compose -f "$PROJECT_DIR/docker-compose.yml" up -d postgres 2>/dev/null || true
    elif brew services start postgresql@15 2>/dev/null; then
        log "PostgreSQL: 已通过 Homebrew 启动"
    else
        warn "PostgreSQL 启动失败，请手动启动"
    fi
fi

# 创建数据库（Homebrew PostgreSQL）
if command -v psql &>/dev/null; then
    /opt/homebrew/opt/postgresql@15/bin/createdb make_movie 2>/dev/null || true
    /opt/homebrew/opt/postgresql@15/bin/psql -d make_movie -c \
        "CREATE USER admin WITH PASSWORD 'password' SUPERUSER;" 2>/dev/null || true
fi

log "PostgreSQL: 端口 5432"

# --- MinIO ---
if pgrep -f "minio server" &>/dev/null; then
    log "MinIO: 已在运行"
elif command -v docker &>/dev/null; then
    docker compose -f "$PROJECT_DIR/docker-compose.yml" up -d minio 2>/dev/null || true
elif command -v minio &>/dev/null; then
    mkdir -p /tmp/minio-data
    MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin \
        minio server /tmp/minio-data --console-address ":9001" &
    log "MinIO: 已通过 Homebrew 启动"
else
    warn "MinIO 未启动，请手动启动或安装: brew install minio/stable/minio"
fi
log "MinIO: 端口 9000 (Console: 9001)"

# ============================================================
# Step 4: 启动后端 (Spring Boot)
# ============================================================
step "4/5 启动后端 (Spring Boot)"

cd "$BACKEND_DIR"

# 首次运行自动编译（跳过测试编译）
if [ ! -f "target/make-movie-0.1.0-SNAPSHOT.jar" ]; then
    log "首次启动，正在编译后端..."
    mvn package -Dmaven.test.skip=true -q
fi

log "后端启动中..."

"$JAVA_HOME/bin/java" -jar target/make-movie-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev 2>&1 &
BACKEND_PID=$!

# 等待后端就绪
log "等待后端就绪 (http://localhost:8080)..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"' ; then
        log "后端已就绪"
        break
    fi
    # Spring Boot 可能返回 404 但进程已在监听
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/projects 2>/dev/null | grep -q "200\|404\|401\|403" ; then
        log "后端已就绪"
        break
    fi
    sleep 2
done

# ============================================================
# Step 5: 启动前端 (Vite)
# ============================================================
step "5/5 启动前端 (Vite)"

cd "$FRONTEND_DIR"

# 首次运行装依赖
if [ ! -d "node_modules" ]; then
    log "安装前端依赖..."
    npm install --silent
fi

log "前端启动中..."
npm run dev 2>&1 &
FRONTEND_PID=$!

sleep 3

# ============================================================
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                              ║${NC}"
echo -e "${GREEN}║   🎬  MakeMovie 启动完成！                    ║${NC}"
echo -e "${GREEN}║                                              ║${NC}"
echo -e "${GREEN}║   前端:  http://localhost:3000               ║${NC}"
echo -e "${GREEN}║   后端:  http://localhost:8080               ║${NC}"
echo -e "${GREEN}║   Swagger: http://localhost:8080/swagger-ui.html ║${NC}"
echo -e "${GREEN}║   MinIO:  http://localhost:9001              ║${NC}"
echo -e "${GREEN}║                                              ║${NC}"
echo -e "${GREEN}║   按 Ctrl+C 停止所有服务                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# 保持脚本运行，等待用户 Ctrl+C
wait
