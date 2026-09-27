# Docker & Kubernetes Comprehensive Guide

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Docker Implementation](#docker-implementation)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [Local Development](#local-development)
6. [Production Deployment](#production-deployment)
7. [Monitoring & Observability](#monitoring--observability)
8. [Security](#security)
9. [Disaster Recovery](#disaster-recovery)
10. [Troubleshooting](#troubleshooting)

## Overview

This guide covers the complete Docker and Kubernetes implementation for the eCommerce microservices platform. It includes best practices for containerization, multi-environment deployment, security hardening, and operational excellence.

### Key Components

- **Docker**: Container images with multi-stage builds and security hardening
- **Docker Compose**: Local development and testing environments
- **Kubernetes**: Production-grade orchestration with auto-scaling and HA
- **Monitoring**: Prometheus, Grafana, and ELK stack integration
- **CI/CD**: Automated build, test, and deployment pipelines

## Architecture

### Microservices Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Load Balancer / Ingress              │
└────────────────────────┬────────────────────────────────┘
                         │
                    ┌────▼────┐
                    │ API     │
                    │ Gateway │
                    └────┬────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    ┌───▼───┐      ┌─────▼─────┐      ┌──▼──┐
    │Customer│      │   Order   │      │Inv. │
    │Service │      │  Service  │      │Srv. │
    └───┬───┘      └─────┬─────┘      └──┬──┘
        │                │                │
        └────────┬───────┼───────┬────────┘
                 │       │       │
            ┌────▼──┐ ┌──▼─────┐ ┌──▼──┐
            │Payment│ │ Kafka  │ │Redis│
            │Service│ │ Bus    │ │Cache│
            └───────┘ └────────┘ └─────┘
                 │       │       │
            ┌────▼───────▼───────▼────┐
            │   PostgreSQL Database   │
            └─────────────────────────┘
```

### Multi-Environment Setup

```
Development         Staging             Production
   (dev)            (staging)             (prod)
   ├─ 2 replicas    ├─ 3 replicas       ├─ 5+ replicas
   ├─ 10Gi storage  ├─ 20Gi storage     ├─ 100Gi storage
   ├─ 256Mi memory  ├─ 512Mi memory     ├─ 1Gi+ memory
   └─ Basic network └─ Enhanced network └─ Advanced network
     policies         policies            policies
```

## Docker Implementation

### Dockerfile Best Practices

Our Dockerfiles follow these best practices:

1. **Multi-stage builds**: Separate build and runtime stages
2. **Non-root users**: All applications run as appuser (UID 1000)
3. **Alpine base images**: Smaller image footprint (~150MB)
4. **Security scanning**: Regular vulnerability scans with Trivy
5. **Layer caching**: Optimal build performance

### Building Docker Images

```bash
# Build specific service
docker build -f Dockerfile.api-gateway -t micro-ecommerce:api-gateway:v1.0.0 .

# Build all services
for service in api-gateway customer-service order-service inventory-service payment-service; do
  docker build -f Dockerfile.$service -t micro-ecommerce:$service:v1.0.0 .
done

# Build with security scanning
docker build --build-arg BUILDKIT_INLINE_CACHE=1 \
  -f Dockerfile.api-gateway \
  -t micro-ecommerce:api-gateway:v1.0.0 .
```

### Image Registry

Push images to GitHub Container Registry (GHCR):

```bash
# Login to GHCR
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin

# Tag and push
docker tag micro-ecommerce:api-gateway:v1.0.0 ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0
docker push ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes 1.20+
- kubectl configured
- Helm 3.0+ (optional)
- 2GB+ available memory

### Deploying to Kubernetes

1. **Create namespaces**:
```bash
kubectl apply -f k8s/00-namespaces.yaml
```

2. **Create secrets**:
```bash
kubectl apply -f k8s/01-secrets.yaml
```

3. **Create configmaps**:
```bash
kubectl apply -f k8s/02-configmaps.yaml
```

4. **Create network policies**:
```bash
kubectl apply -f k8s/03-network-policies.yaml
```

5. **Create RBAC**:
```bash
kubectl apply -f k8s/04-rbac.yaml
```

6. **Create storage**:
```bash
kubectl apply -f k8s/05-storage-classes.yaml
```

7. **Deploy infrastructure services**:
```bash
kubectl apply -f k8s/03-infrastructure.yaml
kubectl apply -f k8s/04-discovery-server.yaml
kubectl apply -f k8s/05-config-server.yaml
```

8. **Deploy microservices**:
```bash
kubectl apply -f k8s/06-api-gateway.yaml
kubectl apply -f k8s/07-customer-service.yaml
kubectl apply -f k8s/08-order-service.yaml
kubectl apply -f k8s/09-inventory-service.yaml
kubectl apply -f k8s/10-payment-service.yaml
```

9. **Deploy autoscaling and policies**:
```bash
kubectl apply -f k8s/06-autoscaling-hpa.yaml
kubectl apply -f k8s/07-pod-disruption-budgets.yaml
kubectl apply -f k8s/08-ingress.yaml
kubectl apply -f k8s/09-pod-security.yaml
```

10. **Verify deployment**:
```bash
kubectl get all -n ecommerce-dev
kubectl get pods -n ecommerce-dev
kubectl get svc -n ecommerce-dev
kubectl logs -n ecommerce-dev deployment/api-gateway
```

### Deployment Status

```bash
# Check deployment status
kubectl rollout status deployment/api-gateway -n ecommerce-dev

# Get detailed pod information
kubectl get pods -n ecommerce-dev -o wide

# Describe specific pod
kubectl describe pod <pod-name> -n ecommerce-dev

# Check resource usage
kubectl top nodes
kubectl top pods -n ecommerce-dev
```

## Local Development

### Using Docker Compose

Start all services locally:

```bash
# Production-like setup
docker-compose up -d

# Development setup with hot-reload
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# View logs
docker-compose logs -f api-gateway

# Stop services
docker-compose down
```

### Development Environment Benefits

- **Hot reload**: Code changes reflect immediately
- **Debug ports**: Remote debugging available on ports 5005-5009
- **PostgreSQL database**: Persistent development database
- **Redis cache**: In-memory caching for development
- **Full monitoring**: Prometheus and Grafana for local metrics
- **Log aggregation**: Kibana for log analysis

### Accessing Services

| Service | URL | Credentials |
|---------|-----|-------------|
| API Gateway | http://localhost:8080 | N/A |
| Customer Service | http://localhost:8081 | N/A |
| Order Service | http://localhost:8082 | N/A |
| Payment Service | http://localhost:8084 | N/A |
| Discovery Server | http://localhost:8761 | N/A |
| Grafana | http://localhost:3000 | admin/admin123 |
| Kibana | http://localhost:5601 | N/A |
| Prometheus | http://localhost:9090 | N/A |

## Production Deployment

### Environment Configuration

Use environment-specific values:

```bash
# Development
kubectl apply -f k8s/01-secrets.yaml -n ecommerce-dev
kubectl apply -f k8s/02-configmaps.yaml -n ecommerce-dev

# Staging
kubectl apply -f k8s/01-secrets.yaml -n ecommerce-staging
kubectl apply -f k8s/02-configmaps.yaml -n ecommerce-staging

# Production
kubectl apply -f k8s/01-secrets.yaml -n ecommerce-prod
kubectl apply -f k8s/02-configmaps.yaml -n ecommerce-prod
```

### Blue-Green Deployment

For zero-downtime deployments:

```bash
./scripts/deploy-blue-green.sh api-gateway ghcr.io/org/api-gateway:v2.0.0 ecommerce-prod
```

### Canary Deployment

For gradual rollout with monitoring:

```bash
./scripts/deploy-canary.sh customer-service ghcr.io/org/customer-service:v2.0.0 ecommerce-prod 10
```

### Scaling Services

```bash
# Manual scaling
kubectl scale deployment api-gateway --replicas=5 -n ecommerce-prod

# Auto-scaling is configured via HPA
# Automatically scales between minReplicas and maxReplicas
# based on CPU and memory utilization
```

## Monitoring & Observability

### Prometheus Metrics

Access Prometheus at http://localhost:9090

Useful queries:
```promql
# Pod restart rate
rate(kube_pod_container_status_restarts_total[5m])

# CPU usage
sum(rate(container_cpu_usage_seconds_total[5m])) by (pod)

# Memory usage
sum(container_memory_usage_bytes) by (pod)

# Request latency
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
```

### Grafana Dashboards

Pre-configured dashboards available at http://localhost:3000:
- Kubernetes Cluster Overview
- Pod Resource Usage
- API Gateway Metrics
- Service Latency
- Error Rates

### Log Aggregation

Access Kibana at http://localhost:5601

Search logs by:
- Service name
- Log level
- Request ID
- Timestamp range

### Distributed Tracing

Optional: Set up Jaeger for distributed tracing:

```bash
# Deploy Jaeger
kubectl apply -f k8s/jaeger-setup.yaml
```

## Security

### Docker Security

1. **Image scanning**: Run Trivy before deployment
```bash
trivy image ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0
```

2. **Non-root users**: All containers run as appuser (UID 1000)
3. **Read-only filesystem**: App jar is read-only (chmod 440)
4. **Resource limits**: Prevent resource exhaustion attacks

### Kubernetes Security

1. **Network Policies**: Control traffic between services
2. **Pod Security Policies**: Enforce security standards
3. **RBAC**: Restrict API access
4. **Secrets management**: Use external secret store in production
5. **Pod security contexts**: Non-root, drop capabilities

### TLS/HTTPS

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.12.0/cert-manager.yaml

# Create certificate issuer
kubectl apply -f k8s/cert-issuer.yaml

# Ingress with TLS
kubectl apply -f k8s/08-ingress.yaml
```

## Disaster Recovery

### Backup Procedures

```bash
# Backup database
./scripts/backup-database.sh ecommerce-prod

# Backup Kubernetes resources
kubectl get all -A -o yaml > ecommerce-backup-$(date +%Y%m%d).yaml

# Backup persistent volumes
# Depends on your storage provider (AWS EBS, Azure Disks, etc.)
```

### Restore Procedures

```bash
# Restore database
./scripts/restore-database.sh ecommerce-prod backup-20240101.sql

# Restore Kubernetes resources
kubectl apply -f ecommerce-backup-20240101.yaml

# Verify restoration
kubectl get all -n ecommerce-prod
```

### High Availability Setup

- **Multiple replicas**: Minimum 2-3 replicas per service
- **Pod disruption budgets**: Ensure availability during updates
- **Node affinity**: Distribute pods across availability zones
- **Database replication**: Master-slave or multi-master setup

## Troubleshooting

### Common Issues

#### 1. Pod Not Starting
```bash
# Check pod status
kubectl describe pod <pod-name> -n ecommerce-dev

# Check logs
kubectl logs <pod-name> -n ecommerce-dev

# Check events
kubectl get events -n ecommerce-dev --sort-by='.lastTimestamp'
```

#### 2. Image Pull Errors
```bash
# Verify image exists
docker image inspect ghcr.io/org/service:tag

# Check registry credentials
kubectl get secrets -n ecommerce-dev ghcr-secret

# Recreate secret if needed
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<username> \
  --docker-password=<token> \
  -n ecommerce-dev
```

#### 3. Service Connectivity Issues
```bash
# Test internal DNS
kubectl run -it --rm debug --image=alpine --restart=Never -- sh
nslookup api-gateway.ecommerce-dev.svc.cluster.local

# Test service connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- sh
curl http://api-gateway:8080/actuator/health
```

#### 4. Resource Constraints
```bash
# Check node resources
kubectl top nodes

# Check pod resource usage
kubectl top pods -n ecommerce-dev

# Check resource quotas
kubectl get resourcequotas -n ecommerce-dev
```

### Performance Optimization

1. **Tuning JVM settings**: `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0`
2. **Database connection pooling**: HikariCP configuration
3. **Cache strategy**: Redis for distributed caching
4. **Batch processing**: Kafka for event streaming

---

## Additional Resources

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Prometheus Querying](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana Dashboard Documentation](https://grafana.com/docs/)

For issues or questions, please refer to the troubleshooting guide or contact the DevOps team.
