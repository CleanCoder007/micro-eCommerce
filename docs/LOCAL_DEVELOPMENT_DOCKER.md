# Local Development Setup with Docker

## Quick Start Guide

Get the entire eCommerce platform running locally in minutes with Docker Compose.

## Prerequisites

- Docker Desktop (Mac/Windows) or Docker Engine (Linux)
- Docker Compose 2.0+
- Git
- At least 4GB free RAM
- 10GB free disk space

## Installation Steps

### 1. Clone Repository

```bash
git clone https://github.com/org/micro-ecommerce.git
cd micro-ecommerce
```

### 2. Setup Environment Variables

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your local configuration
nano .env
```

Key variables for local development:
```env
# Database
DB_HOST=postgres
DB_PASSWORD=password

# Redis
REDIS_HOST=redis
REDIS_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# Logging
LOG_LEVEL=DEBUG
SPRING_PROFILES_ACTIVE=dev
```

### 3. Start Services

#### Development Stack with Hot-Reload

Best for active development - includes debug ports and code volume mounts:

```bash
# Start all services
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Follow logs
docker-compose logs -f

# Stop services
docker-compose down
```

#### Production-Like Stack

For testing in production-like conditions:

```bash
docker-compose -f docker-compose.yml up -d
```

### 4. Verify Services

```bash
# Check container status
docker-compose ps

# Expected output - all containers should be "Up"
NAME                        STATUS
zookeeper-dev              Up
kafka-dev                  Up
config-server-dev          Up
discovery-server-dev       Up
api-gateway-dev            Up
customer-service-dev       Up
order-service-dev          Up
inventory-service-dev      Up
payment-service-dev        Up
postgres-dev               Up
redis-dev                  Up
elasticsearch-dev          Up
kibana-dev                 Up
prometheus-dev             Up
grafana-dev                Up
```

## Accessing Services

### Application Services

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | http://localhost:8080 | Main API entry point |
| Customer Service | http://localhost:8081 | Customer management |
| Order Service | http://localhost:8082 | Order management |
| Payment Service | http://localhost:8084 | Payment processing |
| Discovery Server | http://localhost:8761 | Service registry (Eureka) |
| Config Server | http://localhost:8888 | Configuration management |

### Testing Endpoints

```bash
# Check API Gateway health
curl http://localhost:8080/actuator/health

# List registered services
curl http://localhost:8761/eureka/apps

# Get customer service health
curl http://localhost:8081/actuator/health
```

### Monitoring & Logging

| Tool | URL | Credentials |
|------|-----|-------------|
| **Grafana** | http://localhost:3000 | admin / admin123 |
| **Prometheus** | http://localhost:9090 | N/A |
| **Kibana** | http://localhost:5601 | N/A |
| **Elasticsearch** | http://localhost:9200 | N/A |

## Development Workflows

### 1. Making Code Changes

With the dev stack running, changes to source code automatically trigger rebuilds:

```bash
# Edit a service
nano services/customer-service/src/main/java/com/ecommerce/customer/CustomerController.java

# Service automatically recompiles and restarts (check logs)
docker-compose logs -f customer-service-dev
```

### 2. Remote Debugging

Connect your IDE to remote debug ports:

#### IntelliJ IDEA
1. Run → Edit Configurations
2. Create new "Remote" configuration
3. Set Host: localhost, Port: 5006 (for customer-service)
4. Connect and set breakpoints

#### VS Code
Add to `.vscode/launch.json`:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Debug Customer Service",
            "request": "attach",
            "hostName": "localhost",
            "port": 5006
        }
    ]
}
```

Debug port mapping:
- API Gateway: 5005
- Customer Service: 5006
- Order Service: 5007
- Inventory Service: 5008
- Payment Service: 5009

### 3. Database Access

Connect to PostgreSQL directly:

```bash
# Using psql
PGPASSWORD=password psql -h localhost -U postgres -d ecommerce_dev

# Show tables
\dt

# Query data
SELECT * FROM customers LIMIT 10;

# Using Docker
docker exec -it postgres-dev psql -U postgres -d ecommerce_dev
```

### 4. Redis Operations

```bash
# Connect to Redis CLI
docker exec -it redis-dev redis-cli

# Show all keys
KEYS *

# Get value
GET key-name

# Monitor commands
MONITOR
```

### 5. Kafka Operations

```bash
# Produce message to topic
docker exec -it kafka-dev kafka-console-producer.sh \
  --broker-list localhost:9092 \
  --topic orders

# Consume from topic
docker exec -it kafka-dev kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning

# List topics
docker exec -it kafka-dev kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

## Monitoring Locally

### View Logs

```bash
# All services
docker-compose logs

# Specific service
docker-compose logs -f api-gateway

# Last 100 lines
docker-compose logs --tail=100 api-gateway

# Follow real-time
docker-compose logs -f
```

### Resource Usage

```bash
# Docker stats
docker stats

# Show running containers and resource usage
docker stats --no-stream
```

### Metrics in Grafana

1. Open http://localhost:3000
2. Login with admin/admin123
3. Go to Dashboards → Browse
4. Select "Kubernetes Cluster" or "Pod Resource Usage"

### Logs in Kibana

1. Open http://localhost:5601
2. Create index pattern: `logstash-*`
3. Search logs by:
   - Service: `app=api-gateway`
   - Level: `level=ERROR`
   - Timerange

## Common Tasks

### Rebuild Specific Service

```bash
# Rebuild from Dockerfile
docker-compose build api-gateway

# Rebuild and restart
docker-compose up -d --build api-gateway
```

### View Service Configuration

```bash
# Show environment variables
docker-compose exec customer-service env

# Show running process
docker-compose exec customer-service ps aux

# Check network connectivity
docker-compose exec api-gateway netstat -tlnp
```

### Clear All Data

```bash
# Stop services
docker-compose down

# Remove volumes (deletes data)
docker-compose down -v

# Remove everything including images
docker-compose down -v --remove-orphans
docker image prune -a

# Restart clean
docker-compose up -d
```

### Update Dependencies

```bash
# For Maven-based services
docker-compose exec api-gateway mvn clean install -DskipTests

# Rebuild image after dependency update
docker-compose build --no-cache api-gateway
```

## Troubleshooting

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change port in docker-compose.yml
# ports:
#   - "8090:8080"
```

### Container Won't Start

```bash
# Check logs
docker-compose logs api-gateway

# Check health status
docker-compose ps

# Rebuild image
docker-compose build --no-cache api-gateway

# Start with verbose output
docker-compose up api-gateway
```

### Out of Memory

```bash
# Increase Docker memory allocation
# Docker Desktop → Settings → Resources → Memory → Set to 6GB or more

# Or limit container memory
# In docker-compose.yml:
# services:
#   api-gateway:
#     mem_limit: 512m
```

### Database Connection Errors

```bash
# Check PostgreSQL is running
docker-compose ps postgres-dev

# Check connection
docker-compose exec postgres-dev psql -U postgres -c "SELECT 1"

# Reset database
docker-compose exec postgres-dev psql -U postgres -c "DROP DATABASE IF EXISTS ecommerce_dev;"
docker-compose up -d postgres-dev
```

### Network Connectivity Issues

```bash
# Test DNS resolution
docker-compose exec api-gateway nslookup config-server

# Test service connectivity
docker-compose exec api-gateway curl http://discovery-server:8761/eureka/apps

# Check network
docker network ls
docker network inspect ecommerce_default
```

## Performance Tips

1. **Use BuildKit for faster builds**:
   ```bash
   export DOCKER_BUILDKIT=1
   docker-compose build
   ```

2. **Mount volumes strategically**:
   - Only mount source code, not jar files
   - Use named volumes for data persistence

3. **Limit log verbosity**:
   ```env
   LOG_LEVEL=INFO  # Not DEBUG
   ```

4. **Use separate dev/prod compose files**:
   ```bash
   # Production (no volume mounts)
   docker-compose up

   # Development (with volume mounts)
   docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
   ```

## Next Steps

- Read the main [Docker & Kubernetes Guide](./DOCKER_KUBERNETES_GUIDE.md)
- Deploy to Kubernetes: [Kubernetes Deployment Guide](./KUBERNETES_DEPLOYMENT.md)
- Setup monitoring: [Monitoring & Observability](./MONITORING_OBSERVABILITY.md)
- Security hardening: [Security Hardening](./SECURITY_HARDENING.md)

## Getting Help

- Check logs: `docker-compose logs <service>`
- Common issues: See Troubleshooting section above
- Documentation: [Docker Documentation](https://docs.docker.com/)
- GitHub Issues: Create an issue with docker compose error output
