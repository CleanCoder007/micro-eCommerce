# Security Hardening Guide

## Overview

This guide covers security best practices implemented throughout the eCommerce microservices platform, including Docker container security, Kubernetes security policies, network security, and secret management.

## Table of Contents

1. [Container Security](#container-security)
2. [Kubernetes Security](#kubernetes-security)
3. [Network Security](#network-security)
4. [Secret Management](#secret-management)
5. [API Security](#api-security)
6. [Database Security](#database-security)
7. [Compliance & Auditing](#compliance--auditing)
8. [Security Checklist](#security-checklist)

## Container Security

### Non-Root Users

All containers run as non-root user `appuser` (UID 1000):

```dockerfile
# In all Dockerfiles
RUN addgroup -g 1000 appuser && \
    adduser -u 1000 -G appuser -s /sbin/nologin -D appuser

USER appuser
```

**Verification:**
```bash
docker run --rm micro-ecommerce:api-gateway id
# Output: uid=1000(appuser) gid=1000(appuser) groups=1000(appuser)
```

### File Permissions

Application JAR files are read-only:

```dockerfile
RUN chmod 550 /app && chmod 440 /app/app.jar
```

### Image Scanning

Scan images for vulnerabilities before deployment:

```bash
# Install Trivy
curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# Scan image
trivy image ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0

# High severity findings only
trivy image --severity HIGH,CRITICAL ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0

# Generate report
trivy image -f json -o scan-results.json ghcr.io/org/micro-ecommerce-api-gateway:v1.0.0
```

### Base Images

Use minimal, security-hardened base images:

```dockerfile
# ✅ Good: Alpine Linux (minimal attack surface)
FROM eclipse-temurin:17-jre-alpine

# ✅ Good: Distroless images (no shell)
FROM gcr.io/distroless/java17-debian11

# ❌ Avoid: Full OS images
FROM ubuntu:latest
FROM centos:latest
```

### Resource Limits

Prevent resource exhaustion attacks:

```yaml
# In Kubernetes manifests
resources:
  requests:
    cpu: 250m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi
```

## Kubernetes Security

### Pod Security Policies

Enforce security standards via Pod Security Policies:

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: restricted-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
  - ALL
  volumes:
  - configMap
  - emptyDir
  - secret
  - persistentVolumeClaim
  hostNetwork: false
  hostIPC: false
  hostPID: false
  runAsUser:
    rule: MustRunAsNonRoot
```

Enable enforcement:
```bash
# Add to API server flags
--enable-admission-plugins=PodSecurityPolicy
--admission-control=PodSecurityPolicy
```

### RBAC Configuration

Implement least privilege access:

```yaml
# Create service account
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ecommerce-sa
  namespace: ecommerce-prod

---
# Restrict permissions
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ecommerce-role
  namespace: ecommerce-prod
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]  # Read-only
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get"]  # Limited access
```

### Pod Security Contexts

Enforce security at pod level:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault
      containers:
      - name: api-gateway
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: false
          capabilities:
            drop:
            - ALL
```

## Network Security

### Network Policies

Control traffic between services:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-network-policy
  namespace: ecommerce-prod
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ecommerce-ingress
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: discovery-server
    ports:
    - protocol: TCP
      port: 8761
```

### Ingress TLS/HTTPS

Enforce HTTPS with TLS certificates:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce-ingress
  namespace: ecommerce-prod
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - api.ecommerce.com
    secretName: ecommerce-tls-prod
  rules:
  - host: api.ecommerce.com
    http:
      paths:
      - path: /
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
```

## Secret Management

### Secret Injection

Use Kubernetes secrets, not environment variables in code:

```yaml
# Define secret
apiVersion: v1
kind: Secret
metadata:
  name: ecommerce-secrets
  namespace: ecommerce-prod
type: Opaque
stringData:
  db-password: "REPLACE_WITH_SECURE_PASSWORD"

---
# Reference in deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: customer-service
spec:
  template:
    spec:
      containers:
      - name: customer-service
        env:
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: ecommerce-secrets
              key: db-password
```

### External Secret Stores

Use external secret management in production:

```bash
# Option 1: HashiCorp Vault
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault -n kube-system

# Option 2: AWS Secrets Manager
# Use AWS Secrets Store CSI driver

# Option 3: Azure Key Vault
# Use Azure Keyvault Provider for Secrets Store CSI Driver
```

### Secret Rotation

Implement secret rotation policies:

```bash
# Rotate database password
# 1. Create new secret
kubectl create secret generic ecommerce-secrets-new \
  -n ecommerce-prod \
  --from-literal=db-password="NEW_SECURE_PASSWORD"

# 2. Update deployment to reference new secret
# 3. Restart pods
kubectl rollout restart deployment/customer-service -n ecommerce-prod

# 4. Update database user password
# 5. Delete old secret
kubectl delete secret ecommerce-secrets -n ecommerce-prod
```

## API Security

### JWT Token Security

Secure JWT implementation:

```yaml
# In Spring application
jwt:
  secret: ${JWT_SECRET}  # Load from secret
  expiration: 3600000    # 1 hour
  algorithm: HS512       # Secure algorithm
```

### Rate Limiting

Implement rate limiting on API Gateway:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-gateway-config
data:
  application.yml: |
    server:
      compression:
        enabled: true
    resilience4j:
      ratelimiter:
        instances:
          api-gateway:
            registerHealthIndicator: true
            limitRefreshPeriod: 1m
            limitForPeriod: 1000
            timeoutDuration: 5s
```

### CORS Configuration

Restrict cross-origin requests:

```yaml
spring:
  web:
    cors:
      allowed-origins: "https://trusted-domain.com"
      allowed-methods: "GET,POST,PUT,DELETE"
      allowed-headers: "Content-Type,Authorization"
      max-age: 3600
```

### API Key Management

Secure API key handling:

```yaml
# Do NOT commit API keys
# ✅ Use environment variables
- name: API_KEY
  valueFrom:
    secretKeyRef:
      name: ecommerce-secrets
      key: api-key

# ✅ Use external secret store
# ❌ Do NOT hardcode in code
# ❌ Do NOT commit to git
```

## Database Security

### Database Access Control

```sql
-- Create restricted database user
CREATE ROLE ecommerce_app WITH ENCRYPTED PASSWORD 'secure_password';
GRANT CONNECT ON DATABASE ecommerce TO ecommerce_app;

-- Grant specific permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ecommerce_app;

-- Revoke dangerous permissions
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON DATABASE ecommerce FROM PUBLIC;
```

### Connection Encryption

Enforce SSL/TLS for database connections:

```yaml
# PostgreSQL in docker-compose
environment:
  POSTGRES_HOST_AUTH_METHOD: scram-sha-256
  POSTGRES_INITDB_ARGS: "-c ssl=on -c ssl_cert_file=/etc/ssl/certs/server.crt -c ssl_key_file=/etc/ssl/private/server.key"
```

### Data Encryption

```yaml
# Kubernetes persistent volume encryption
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: encrypted-storage
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-ssd
  replication-type: regional-pd
  encrypted: "true"  # Enable encryption
  kms-key-name: projects/PROJECT/locations/LOCATION/keyRings/RING/cryptoKeys/KEY
```

## Compliance & Auditing

### Audit Logging

Enable audit logging in Kubernetes:

```yaml
# --audit-log-path=/var/log/kubernetes/audit.log
# --audit-log-maxage=7
# --audit-log-maxbackup=10
# --audit-log-maxsize=100

# Audit policy rules
apiVersion: audit.k8s.io/v1
kind: Policy
rules:
- level: RequestResponse
  verbs: ["create", "update", "delete"]
  resources: ["secrets"]
  omitStages:
  - RequestReceived
```

### Application Audit Logs

Log security-relevant events:

```java
// Log authentication attempts
logger.info("User {} login attempt - {}", username, result);

// Log authorization failures
logger.warn("Unauthorized access attempt to {} by user {}", resource, username);

// Log data access
logger.info("Data accessed: {} by user {} at {}", dataId, username, timestamp);
```

### Compliance Checklist

- ✅ OWASP Top 10 mitigations
- ✅ GDPR compliance (data retention, encryption)
- ✅ PCI-DSS compliance (for payment data)
- ✅ SOC 2 controls (access, logging, monitoring)
- ✅ Regular security audits
- ✅ Penetration testing

## Security Checklist

### Pre-Deployment

- [ ] All images scanned with Trivy
- [ ] No secrets in code or Dockerfiles
- [ ] All containers run as non-root
- [ ] Resource limits configured
- [ ] Network policies defined
- [ ] RBAC roles configured
- [ ] Pod security policies enabled
- [ ] TLS/HTTPS enforced

### Runtime

- [ ] Audit logging enabled
- [ ] Monitoring and alerting configured
- [ ] Security events logged
- [ ] Secret rotation policy in place
- [ ] Regular backups tested
- [ ] Incident response plan documented
- [ ] Security team notified

### Regular Maintenance

- [ ] Weekly: Review security logs
- [ ] Monthly: Update dependencies and patches
- [ ] Quarterly: Security audit
- [ ] Annually: Penetration testing
- [ ] As needed: Security incident investigation

## References

- [OWASP Top 10](https://owasp.org/Top10/)
- [Kubernetes Security Documentation](https://kubernetes.io/docs/concepts/security/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

## Getting Help

- Security issues: security@ecommerce.com (do not use public issues)
- Security documentation: See security team wiki
- Regular training: Quarterly security awareness sessions
