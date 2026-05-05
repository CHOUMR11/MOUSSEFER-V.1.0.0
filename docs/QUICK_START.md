# Quick Start — Moussefer Backend

> Get up and running in 5 minutes

---

## Prerequisites

```bash
# Check you have everything installed
docker --version  # v20.10+
docker-compose --version  # v2.0+
java -version  # JDK 21
mvn --version  # Maven 3.9+
```

---

## Start Everything (Docker)

```bash
# 1. Start infrastructure + services (all in containers)
docker-compose up --build

# 2. Wait for all services to be healthy (~1-2 minutes)
# Look for: "service-registry is UP" in logs

# 3. Access the API
curl http://localhost:8080/actuator/health

# 4. Open browser to:
# - API: http://localhost:8080
# - Swagger UI: http://localhost:8080/swagger-ui.html
# - Eureka: http://localhost:8761 (user: eureka, password: eureka123)
# - Kafka UI: http://localhost:9090
```

**Stop everything:**
```bash
docker-compose down
```

---

## Start Infrastructure + IDE (Development)

For developing with IntelliJ, run only the databases:

```bash
# 1. Start infrastructure (databases, kafka, redis)
docker-compose -f docker-compose-infra.yml up -d

# 2. In IntelliJ, click Run on these main classes (in order):
#    1. service-registry: src/main/java/.../ServiceRegistryApplication.java
#    2. api-gateway: src/main/java/.../ApiGatewayApplication.java
#    3. auth-service: src/main/java/.../AuthServiceApplication.java
#    4. Other services (in any order)

# 3. Services will auto-register with Eureka (~10-15 seconds)

# 4. Test:
curl http://localhost:8080/actuator/health
```

**Stop:**
```bash
docker-compose -f docker-compose-infra.yml down
```

---

## First API Call

### 1. Register User

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe",
    "role": "PASSENGER"
  }'

# Response:
# {
#   "token": "eyJhbGc...",
#   "refreshToken": "eyJhbGc...",
#   "userId": "550e8400-e29b-41d4-a716-446655440000",
#   "email": "john@example.com",
#   "role": "PASSENGER"
# }
```

### 2. Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123!"
  }'
```

### 3. Use Token

```bash
TOKEN="eyJhbGc..."  # From register/login response

curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## Environment Variables

### Local Development (already set in `.env`)

```bash
JWT_SECRET=moussefer_dev_jwt_secret_256bits_change_in_prod_moussefer_dev
REDIS_PASSWORD=redis123
MYSQL_ROOT_PASSWORD=root123
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
SWAGGER_ENABLED=true
```

### Production (use `.env.prod`)

```bash
cp .env.prod .env.production

# Edit with real values:
# - Generate JWT_SECRET: openssl rand -base64 32
# - Change all database passwords
# - Add real Stripe keys
# - Set SWAGGER_ENABLED=false
```

---

## Check Service Status

### Eureka Dashboard

http://localhost:8761/eureka/web

Expected output:
- 🟢 service-registry (itself)
- 🟢 api-gateway
- 🟢 auth-service
- 🟢 user-service
- 🟢 trajet-service
- ... (all 11 services)

### Health Check

```bash
curl http://localhost:8080/actuator/health/readiness

# Response should show all services UP:
# {
#   "status": "UP",
#   "components": {
#     "db": { "status": "UP" },
#     "kafka": { "status": "UP" },
#     ...
#   }
# }
```

---

## Database Access

### MySQL

```bash
# Connect to any database
docker exec -it moussefer-mysql-auth mysql -u root -proot123

# List databases
SHOW DATABASES;

# Use auth database
USE moussefer_auth;

# View tables
SHOW TABLES;

# Quick query
SELECT * FROM users LIMIT 5;
```

### MySQL Clients

- **Command line**: `docker exec -it moussefer-mysql-auth mysql ...`
- **DBeaver**: Connection string: `localhost:3306` (user: root, password: root123)
- **MySQL Workbench**: Same credentials, create new MySQL connection
- **IntelliJ**: Database → New → MySQL → localhost:3306

### Redis

```bash
# Connect to Redis
docker exec -it moussefer-redis redis-cli -a redis123

# View keys
KEYS *

# Get session
GET "user-session:550e8400-e29b-41d4-a716-446655440000"

# Monitor live commands
MONITOR
```

---

## Kafka Topics

### View Topics

```bash
docker exec -it moussefer-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

### Monitor Topic

```bash
docker exec -it moussefer-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic user.registered \
  --from-beginning
```

### Kafka UI

Open http://localhost:9090

---

## Common Issues

### "Connection refused" on localhost:8080

```bash
# Check if services are running
docker ps | grep moussefer

# Check if api-gateway is healthy
docker logs moussefer-gateway

# Make sure port 8080 isn't used elsewhere
lsof -i :8080
```

### "Eureka client not registered"

```bash
# Services register within 10-30 seconds
# Wait longer and refresh browser

# Or check service logs
docker logs moussefer-auth | grep -i eureka
```

### "Database connection failed"

```bash
# Check MySQL is running and healthy
docker-compose ps mysql-auth

# Test connection
docker exec -it moussefer-mysql-auth \
  mysqladmin -u root -proot123 ping

# Check credentials in .env
cat .env | grep DB_PASSWORD
```

### "Kafka timeout"

```bash
# Check Kafka is healthy
docker-compose ps kafka

# Check if Kafka is listening
docker exec moussefer-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092
```

---

## Next Steps

1. **Explore APIs**: Visit http://localhost:8080/swagger-ui.html
2. **Create test data**: Use the REST API to create users, trips, etc.
3. **Monitor**: Watch Eureka (8761) and Kafka UI (9090)
4. **Read docs**: Check DEPLOYMENT.md for production setup
5. **Develop**: Modify code in services and restart from IDE

---

## Production Deployment

See **DEPLOYMENT.md** for full production guide including:
- Building Docker images for production
- Environment configuration
- Database backups
- SSL/TLS setup
- Kubernetes deployment
- Monitoring & logging
- Security checklist

---

## Getting Help

```bash
# View service logs
docker-compose logs -f [service-name]

# Restart a service
docker-compose restart [service-name]

# Rebuild a service
docker-compose build [service-name]

# Clean everything
docker-compose down -v
```

---

**That's it! You should have a fully functional Moussefer backend running locally. 🚀**
