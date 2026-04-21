# Deployment Guide — Moussefer Microservices

> Complete guide for running the Moussefer backend locally and in production

---

## Prerequisites

### Required Software
- **Docker & Docker Compose** v2.0+ ([Install](https://docs.docker.com/get-docker/))
- **JDK 21** ([Download](https://www.oracle.com/java/technologies/downloads/#java21))
- **Maven 3.9+** ([Install](https://maven.apache.org/install.html))
- **Git**

### System Requirements
- **Local Development**: 8 GB RAM, 30 GB disk space minimum
- **Production**: 16+ GB RAM, 100+ GB disk space, load balancer

---

## Local Development Setup

### 1. Clone and Prepare

```bash
# Clone the repository
git clone <repo-url>
cd moussefer-backend

# Copy environment template
cp .env.example .env

# Verify .env has correct values (already set for dev)
cat .env
```

### 2. Start Infrastructure Only (Option A: IntelliJ Development)

For local development in your IDE:

```bash
# Start only infrastructure (MySQL, Kafka, Redis, MinIO)
docker-compose -f docker-compose-infra.yml up -d

# Verify all containers are running
docker-compose -f docker-compose-infra.yml ps
```

**Expected Output:**
```
STATUS          NAMES
Up (healthy)    moussefer-zookeeper
Up (healthy)    moussefer-kafka
Up (healthy)    moussefer-redis
Up (healthy)    moussefer-minio
Up (healthy)    moussefer-mysql-auth
...
```

### 3. Run Services from IDE (IntelliJ)

In **IntelliJ IDEA**:

1. Open the parent project
2. For each service, right-click the `*Application.java` main class
3. Select **Run** or press `Shift+F10`

**Startup Order:**
1. `service-registry` (wait for healthy)
2. `api-gateway` (wait for Eureka registration)
3. Other services in any order

**IDE Configuration** (optional, for automatic startup):

Create `.idea/runConfigurations/Startup.xml`:
```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="All Services" type="CompoundRunConfigurationType">
    <toRun name="ServiceRegistryApplication" type="Application" />
    <toRun name="ApiGatewayApplication" type="Application" />
    <toRun name="AuthServiceApplication" type="Application" />
    <!-- Add others as needed -->
    <method v="2" />
  </configuration>
</component>
```

### 4. Start All Services with Docker (Option B: Full Docker)

```bash
# Build all services
mvn clean package -DskipTests

# Start everything
docker-compose up --build

# Wait for all services to register with Eureka (~30 seconds)
```

### 5. Verify Setup

**Access Points:**

| Service | URL | Credentials |
|---|---|---|
| **API Gateway** | http://localhost:8080 | N/A |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | N/A |
| **Eureka Dashboard** | http://localhost:8761 | `eureka` / `eureka123` |
| **Kafka UI** | http://localhost:9090 | N/A |
| **MinIO Console** | http://localhost:9001 | `minioadmin` / `minioadmin` |

**Test API:**

```bash
# Health check
curl http://localhost:8080/actuator/health

# Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!",
    "firstName": "John",
    "lastName": "Doe",
    "role": "PASSENGER"
  }'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }'
```

### 6. Stop Local Environment

```bash
# Stop all containers
docker-compose down

# Or stop infrastructure only
docker-compose -f docker-compose-infra.yml down

# Stop from IDE: Click red stop button in each service tab
```

---

## Production Deployment

### 1. Prepare Production Environment

```bash
# Copy production config
cp .env.prod .env.production

# Edit with real production values
nano .env.production
```

**Generate Strong Secrets:**

```bash
# Generate JWT secret (256 bits)
openssl rand -base64 32

# Generate internal API key (256 bits hex)
openssl rand -hex 32

# Generate passwords (use for all DB passwords)
openssl rand -base64 32
```

### 2. Build Production Images

```bash
# Set production environment
export ENV=production
export REGISTRY=your-docker-registry.com

# Build all services
mvn clean package -DskipTests

# Tag Docker images
docker tag moussefer-api-gateway:latest $REGISTRY/moussefer-api-gateway:v1.0.0
docker tag moussefer-auth-service:latest $REGISTRY/moussefer-auth-service:v1.0.0
# ... tag others

# Push to registry
docker push $REGISTRY/moussefer-api-gateway:v1.0.0
# ... push others
```

### 3. Production Docker Compose

Create `docker-compose.prod.yml`:

```yaml
version: '3.9'

services:
  # Infrastructure with persistence
  mysql-auth:
    image: mysql:8.3
    volumes:
      - /data/mysql-auth:/var/lib/mysql  # Use host volumes
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: moussefer_auth
    restart: always
    # Add backup cron job

  redis:
    image: redis:7.2-alpine
    volumes:
      - /data/redis:/data
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    restart: always

  # Services with resource limits
  api-gateway:
    image: your-registry/moussefer-api-gateway:v1.0.0
    restart: always
    deploy:
      replicas: 3  # Load balance across 3 instances
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
    environment:
      SPRING_PROFILES_ACTIVE: production
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 4. Kubernetes Deployment (Optional)

Create `k8s/api-gateway-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api-gateway
        image: your-registry/moussefer-api-gateway:v1.0.0
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: jwt-secret
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-service
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 8080
```

Deploy to Kubernetes:
```bash
# Create secrets
kubectl create secret generic app-secrets \
  --from-literal=jwt-secret=YOUR_JWT_SECRET

# Deploy
kubectl apply -f k8s/
```

### 5. Database Backup Strategy

```bash
# Daily backup script (backup.sh)
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/moussefer"

for service in auth user trajet reservation payment notification chat voyage demande avis; do
  docker exec moussefer-mysql-$service mysqldump \
    -u root -p$MYSQL_ROOT_PASSWORD \
    moussefer_$service > $BACKUP_DIR/$service-$DATE.sql.gz
done

# Upload to S3
aws s3 sync $BACKUP_DIR s3://your-backup-bucket/
```

Schedule with cron:
```bash
0 2 * * * /opt/moussefer/backup.sh  # Daily at 2 AM
```

### 6. Monitoring & Logging

**Environment Variables for Production:**

```bash
# Application logs
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_MOUSSEFER=DEBUG

# Metrics export
MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true

# Database monitoring
SPRING_JPA_SHOW_SQL=false
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20
SPRING_DATASOURCE_HIKARI_LEAK_DETECTION_THRESHOLD=60000
```

**Prometheus Scrape Config:**

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'moussefer'
    static_configs:
      - targets: ['localhost:8080', 'localhost:8081', 'localhost:8082']
    metrics_path: '/actuator/prometheus'
```

### 7. SSL/TLS Configuration

**Nginx Reverse Proxy:**

```nginx
upstream moussefer_api {
    server api-gateway:8080;
    server api-gateway-2:8080;
    server api-gateway-3:8080;
}

server {
    listen 443 ssl http2;
    server_name api.moussefer.com;

    ssl_certificate /etc/ssl/certs/moussefer.crt;
    ssl_certificate_key /etc/ssl/private/moussefer.key;

    location / {
        proxy_pass http://moussefer_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Troubleshooting

### Service Won't Start

```bash
# Check logs
docker logs moussefer-auth  # or docker-compose logs auth-service

# Common issues:
# - Database not ready: wait with healthchecks
# - Port already in use: change docker-compose ports
# - Out of memory: increase Docker memory allocation
```

### Database Connection Failed

```bash
# Test MySQL connection
docker exec moussefer-mysql-auth \
  mysqladmin -u root -proot123 ping

# Check environment variables
docker inspect moussefer-auth | grep -A 20 Env
```

### Services Not Registering with Eureka

```bash
# Check Eureka dashboard: http://localhost:8761
# Should see all services listed

# If missing, check service logs:
docker logs moussefer-auth | grep -i eureka
```

### Kafka Connection Issues

```bash
# Verify Kafka is healthy
docker-compose ps kafka

# Check if topics are created
docker exec moussefer-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

---

## Performance Tuning

### JVM Settings

Create `setenv.sh` in each service:

```bash
export JAVA_OPTS="-Xms512m -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.jmx.enabled=true"
```

### MySQL Optimization

```sql
-- Connection pooling (already configured with HikariCP)
-- Max pool size: 20

-- Enable query cache
SET GLOBAL query_cache_type = 1;
SET GLOBAL query_cache_size = 268435456;  -- 256MB

-- Index optimization
CREATE INDEX idx_trajet_departure_date ON moussefer_trajet(departure_date);
CREATE INDEX idx_reservation_status ON moussefer_reservation(status);
```

### Redis Optimization

```bash
# Configure for production
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
redis-cli CONFIG SET appendonly yes
```

---

## Security Checklist

- [ ] All default credentials changed (MySQL, Redis, Eureka, MinIO)
- [ ] JWT_SECRET is 256+ bits (32+ characters)
- [ ] SSL/TLS enabled on all external endpoints
- [ ] CORS origins restricted to known domains
- [ ] Database backups automated and tested
- [ ] Logs centralized and monitored
- [ ] Metrics and alerting configured
- [ ] Rate limiting enabled on API Gateway
- [ ] Regular security updates for dependencies
- [ ] Database credentials stored in secrets manager
- [ ] Stripe webhook signatures validated
- [ ] Email credentials not hardcoded

---

## Scaling Considerations

### Horizontal Scaling

```bash
# Run multiple instances of stateless services
docker-compose up --scale api-gateway=3 --scale auth-service=2
```

### Vertical Scaling

Increase Docker memory allocation and JVM heap:

```bash
# In docker-compose.yml
environment:
  JAVA_OPTS: -Xmx2g -XX:+UseG1GC
```

### Caching Strategy

- **Redis Cache**: Trajet search results (5-minute TTL)
- **Database Indices**: Frequently queried columns
- **CDN**: Static assets (frontend)

---

## Rollback Procedure

```bash
# Keep previous version image
docker tag moussefer-api-gateway:v1.0.0 moussefer-api-gateway:v1.0.0-backup

# Deploy new version
docker-compose up api-gateway

# If issues, rollback
docker-compose up -d moussefer-api-gateway:v1.0.0-backup
```

---

## Support & Additional Resources

- **Eureka Dashboard**: http://your-domain:8761
- **Kafka UI**: http://your-domain:9090
- **Health Checks**: http://your-domain/actuator/health
- **API Documentation**: http://your-domain/swagger-ui.html

For issues, check service logs:
```bash
docker-compose logs --tail=100 -f [service-name]
```
