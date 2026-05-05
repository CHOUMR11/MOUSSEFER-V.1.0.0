#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# MOUSSEFER — Build & Deploy Script
# Compiles all 17 microservices then optionally starts Docker Compose
# ─────────────────────────────────────────────────────────────────────────────
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     MOUSSEFER — Build All Services       ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"

# Check prerequisites
command -v java >/dev/null 2>&1 || { echo -e "${RED}❌ JDK 21 is required but not installed.${NC}"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo -e "${RED}❌ Maven 3.9+ is required but not installed.${NC}"; exit 1; }

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Java 21+ recommended. Current: $(java -version 2>&1 | head -1)${NC}"
fi

# Step 1: Build parent POM
echo -e "\n${YELLOW}[1/2] Building parent POM...${NC}"
mvn clean install -N -DskipTests -q

# Step 2: Build all services
echo -e "${YELLOW}[2/2] Building all 17 microservices (skip tests)...${NC}"
mvn package -DskipTests -q

echo -e "${GREEN}✅ All services compiled successfully!${NC}"

# Step 3: Check for .env
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠️  No .env file found. Copying from .env.example...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}   → Edit .env with your values before starting Docker.${NC}"
fi

# Step 4: Docker (optional)
if [ "$1" == "--docker" ] || [ "$1" == "-d" ]; then
    echo -e "\n${YELLOW}[3/3] Starting Docker Compose...${NC}"
    command -v docker >/dev/null 2>&1 || { echo -e "${RED}❌ Docker is required.${NC}"; exit 1; }
    docker compose up --build -d
    echo -e "${GREEN}✅ All containers starting. Check logs: docker compose logs -f${NC}"
elif [ "$1" == "--infra" ] || [ "$1" == "-i" ]; then
    echo -e "\n${YELLOW}[3/3] Starting infrastructure only (for IDE development)...${NC}"
    docker compose -f docker-compose-infra.yml up -d
    echo -e "${GREEN}✅ Infrastructure ready. Start services from your IDE.${NC}"
else
    echo -e "\n${YELLOW}Next steps:${NC}"
    echo -e "  Full Docker:  ${GREEN}./build.sh --docker${NC}"
    echo -e "  IDE mode:     ${GREEN}./build.sh --infra${NC}  (then run services from IntelliJ)"
    echo -e "  Manual:       ${GREEN}docker compose up --build${NC}"
fi

echo -e "\n${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║         Build Complete! 🚀                ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo -e "Services: http://localhost:8080  |  Eureka: http://localhost:8761"
echo -e "Swagger:  http://localhost:8080/swagger-ui.html"
echo -e "Kafka UI: http://localhost:9090  |  MinIO: http://localhost:9001"
