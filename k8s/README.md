# Kubernetes Manifests

Complete Kubernetes manifests for multi-environment deployment of the eCommerce microservices platform.

## File Structure

```
k8s/
├── 00-namespaces.yaml          # Namespace definitions (dev, staging, prod, monitoring)
├── 01-secrets.yaml             # Secrets for all environments
├── 02-configmaps.yaml          # Configuration for all services
├── 03-network-policies.yaml    # Network policies for security
├── 04-rbac.yaml                # Role-based access control
├── 05-storage-classes.yaml     # Storage classes and PVCs
├── 06-autoscaling-hpa.yaml     # Horizontal Pod Autoscalers
├── 07-pod-disruption-budgets.yaml # Pod disruption budgets for HA
├── 08-ingress.yaml             # Ingress rules for traffic routing
├── 09-pod-security.yaml        # Pod security policies
├── 03-infrastructure.yaml      # PostgreSQL, Redis, Kafka (existing)
├── 04-discovery-server.yaml    # Eureka discovery server
├── 05-config-server.yaml       # Spring Config Server
├── 06-api-gateway.yaml         # API Gateway deployment
├── 07-customer-service.yaml    # Customer Service deployment
├── 08-order-service.yaml       # Order Service deployment
├── 09-inventory-service.yaml   # Inventory Service deployment
└── 10-payment-service.yaml     # Payment Service deployment
```

## Deployment Order

Apply manifests in this order:

```bash
# 1. Namespaces first
kubectl apply -f 00-namespaces.yaml

# 2. Secrets and ConfigMaps
kubectl apply -f 01-secrets.yaml
kubectl apply -f 02-configmaps.yaml

# 3. RBAC and security
kubectl apply -f 04-rbac.yaml
kubectl apply -f 09-pod-security.yaml

# 4. Storage
kubectl apply -f 05-storage-classes.yaml

# 5. Network policies (after RBAC)
kubectl apply -f 03-network-policies.yaml

# 6. Infrastructure services
kubectl apply -f 03-infrastructure.yaml
kubectl apply -f 04-discovery-server.yaml
kubectl apply -f 05-config-server.yaml

# 7. Microservices
kubectl apply -f 06-api-gateway.yaml
kubectl apply -f 07-customer-service.yaml
kubectl apply -f 08-order-service.yaml
kubectl apply -f 09-inventory-service.yaml
kubectl apply -f 10-payment-service.yaml

# 8. Advanced features
kubectl apply -f 06-autoscaling-hpa.yaml
kubectl apply -f 07-pod-disruption-budgets.yaml
kubectl apply -f 08-ingress.yaml
```

Or apply all at once (after adjusting namespaces):
```bash
kubectl apply -f k8s/
```

## Environment-Specific Deployment

### Development Environment

```bash
# Deploy to development namespace
kubectl apply -f k8s/ -n ecommerce-dev

# Verify
kubectl get all -n ecommerce-dev
kubectl get pods -n ecommerce-dev -w  # Watch pods starting
```

### Staging Environment

```bash
# Deploy to staging namespace
kubectl apply -f k8s/ -n ecommerce-staging

# Verify
kubectl get all -n ecommerce-staging
```

### Production Environment

```bash
# Deploy to production namespace
kubectl apply -f k8s/ -n ecommerce-prod

# Verify
kubectl get all -n ecommerce-prod

# Check resource requests/limits
kubectl get pods -n ecommerce-prod -o json | jq '.items[].spec.containers[].resources'
```

## Configuration Management

### Update Secrets

**Important**: Never commit actual secrets to git!

```bash
# Update database password
kubectl create secret generic ecommerce-secrets \
  --from-literal=db-password='new-secure-password' \
  -n ecommerce-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart affected deployments
kubectl rollout restart deployment/customer-service -n ecommerce-prod
kubectl rollout restart deployment/order-service -n ecommerce-prod
```

### Update ConfigMaps

```bash
# Edit configuration
kubectl edit configmap api-gateway-config -n ecommerce-dev

# Or apply from file
kubectl apply -f k8s/02-configmaps.yaml -n ecommerce-dev

# Restart to pick up changes
kubectl rollout restart deployment/api-gateway -n ecommerce-dev
```

### Environment-Specific Values

Customize these files for each environment:

#### Secrets (01-secrets.yaml)
- Database passwords
- JWT secrets
- API keys
- Registry credentials

#### ConfigMaps (02-configmaps.yaml)
- Service configuration
- Logging levels
- Feature flags
- External service URLs

#### Resource Limits (in deployment files)
- CPU requests/limits
- Memory requests/limits
- Based on environment workload

#### Replicas (in deployment files)
```yaml
# Development: 2 replicas
spec:
  replicas: 2

# Staging: 3 replicas
spec:
  replicas: 3

# Production: 5+ replicas
spec:
  replicas: 5
```

## Verification Checklist

After deployment, verify:

```bash
# 1. All pods running
kubectl get pods -n ecommerce-dev
# Expected: All pods should be Running and Ready

# 2. Services created
kubectl get svc -n ecommerce-dev

# 3. Network policies applied
kubectl get networkpolicies -n ecommerce-dev

# 4. Autoscaling configured
kubectl get hpa -n ecommerce-dev

# 5. Pod disruption budgets
kubectl get pdb -n ecommerce-dev

# 6. Ingress rules
kubectl get ingress -n ecommerce-dev

# 7. Check pod readiness
kubectl get pods -n ecommerce-dev -o jsonpath='{.items[*].status.containerStatuses[*].ready}'

# 8. Check resource usage
kubectl top pods -n ecommerce-dev

# 9. View service endpoints
kubectl get endpoints -n ecommerce-dev
```

## Scaling

### Manual Scaling

```bash
# Scale deployment to specific replicas
kubectl scale deployment api-gateway --replicas=3 -n ecommerce-prod

# Verify
kubectl get deployment api-gateway -n ecommerce-prod
```

### Auto-Scaling (HPA)

Auto-scaling is configured in `06-autoscaling-hpa.yaml`:

```bash
# View HPA status
kubectl get hpa -n ecommerce-dev

# Describe HPA for details
kubectl describe hpa api-gateway-hpa -n ecommerce-dev

# Monitor scaling decisions
kubectl get hpa -n ecommerce-dev -w

# Manual HPA adjustment
kubectl patch hpa api-gateway-hpa -n ecommerce-dev -p '{"spec":{"maxReplicas":10}}'
```

## Updating Deployments

### Rolling Update

```bash
# Update image
kubectl set image deployment/api-gateway \
  api-gateway=ghcr.io/org/api-gateway:v2.0.0 \
  -n ecommerce-prod

# Monitor rollout
kubectl rollout status deployment/api-gateway -n ecommerce-prod

# Check rollout history
kubectl rollout history deployment/api-gateway -n ecommerce-prod

# Rollback if needed
kubectl rollout undo deployment/api-gateway -n ecommerce-prod
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

## Network Access

### Port Forwarding

Access services locally:

```bash
# API Gateway
kubectl port-forward svc/api-gateway 8080:80 -n ecommerce-dev

# Grafana
kubectl port-forward svc/grafana-ui 3000:3000 -n ecommerce-monitoring

# Prometheus
kubectl port-forward svc/prometheus-ui 9090:9090 -n ecommerce-monitoring
```

### Service Discovery

Services are discoverable via DNS:

```bash
# Full DNS name
<service>.<namespace>.svc.cluster.local

# Examples
api-gateway.ecommerce-dev.svc.cluster.local
customer-service.ecommerce-prod.svc.cluster.local

# From within cluster, use short name
curl http://api-gateway:8080/actuator/health
```

## Monitoring

### Resource Usage

```bash
# Node resources
kubectl top nodes

# Pod resources
kubectl top pods -n ecommerce-dev

# Resource requests vs actual usage
kubectl describe node <node-name>
```

### Pod Status

```bash
# Get pod details
kubectl describe pod <pod-name> -n ecommerce-dev

# Check events
kubectl get events -n ecommerce-dev --sort-by='.lastTimestamp'

# View logs
kubectl logs <pod-name> -n ecommerce-dev
kubectl logs -f <pod-name> -n ecommerce-dev  # Follow

# Multiple containers
kubectl logs <pod-name> -c <container-name> -n ecommerce-dev
```

## Troubleshooting

### Pod not starting

```bash
# Check pod status
kubectl get pods -n ecommerce-dev -o wide

# Describe pod for error details
kubectl describe pod <pod-name> -n ecommerce-dev

# Check logs
kubectl logs <pod-name> -n ecommerce-dev --previous  # Previous run if crashed

# Check resource availability
kubectl describe nodes
```

### Service connectivity issues

```bash
# Verify service exists
kubectl get svc <service-name> -n ecommerce-dev

# Check endpoints
kubectl get endpoints <service-name> -n ecommerce-dev

# Test DNS resolution
kubectl run -it --rm debug --image=alpine --restart=Never -- nslookup <service-name>

# Test connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- curl http://<service>:8080
```

### Out of resources

```bash
# Check resource quotas
kubectl get resourcequotas -n ecommerce-dev

# Check node capacity
kubectl describe nodes

# Adjust resource limits in manifests and reapply
```

## Best Practices

1. **Always test in development first**
2. **Use namespaces for environment isolation**
3. **Never commit secrets**
4. **Document all customizations**
5. **Monitor resource usage**
6. **Implement health checks**
7. **Use rolling updates for gradual rollout**
8. **Maintain backup of all manifests**
9. **Version control all manifests**
10. **Review and audit regularly**

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Security Best Practices](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

## Support

For issues or questions:
1. Check logs: `kubectl logs <pod-name> -n <namespace>`
2. Describe resource: `kubectl describe pod <pod-name> -n <namespace>`
3. Review manifests for configuration errors
4. Contact DevOps team for cluster issues
