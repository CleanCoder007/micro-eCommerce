#!/bin/bash
set -euo pipefail

# Blue-Green Deployment Script
# This script implements zero-downtime deployments using blue-green strategy
# Usage: ./deploy-blue-green.sh <service-name> <image:tag> <namespace>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_NAME="${1:?Service name required}"
IMAGE="${2:?Image tag required (e.g., ghcr.io/org/service:v1.0.0)}"
NAMESPACE="${3:?Namespace required (ecommerce-dev|ecommerce-staging|ecommerce-prod)}"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

validate_inputs() {
    if ! kubectl get namespace "$NAMESPACE" &>/dev/null; then
        log_error "Namespace $NAMESPACE does not exist"
        exit 1
    fi

    if ! kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" &>/dev/null; then
        log_error "Deployment $SERVICE_NAME not found in namespace $NAMESPACE"
        exit 1
    fi

    log_info "Inputs validated successfully"
}

get_current_replicas() {
    kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}'
}

create_green_deployment() {
    log_info "Creating green deployment..."

    # Get the current deployment template
    GREEN_DEPLOY="${SERVICE_NAME}-green"

    # Create green deployment by copying blue with new image
    kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" -o yaml | \
        sed "s/$SERVICE_NAME/$GREEN_DEPLOY/g" | \
        sed "s|image:.*|image: $IMAGE|g" | \
        kubectl apply -f -

    log_info "Green deployment created: $GREEN_DEPLOY"
}

wait_for_ready() {
    local deployment=$1
    local timeout=300
    local elapsed=0

    log_info "Waiting for $deployment to be ready (timeout: ${timeout}s)..."

    while [ $elapsed -lt $timeout ]; do
        READY=$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Progressing")].status}')

        if [ "$READY" == "True" ]; then
            log_info "$deployment is ready"
            return 0
        fi

        sleep 5
        elapsed=$((elapsed + 5))
        echo -n "."
    done

    log_error "$deployment failed to be ready within ${timeout}s"
    return 1
}

run_smoke_tests() {
    log_info "Running smoke tests on green deployment..."

    # Port-forward to green service for testing
    PODS=$(kubectl get pods -n "$NAMESPACE" -l app="${SERVICE_NAME}-green" -o jsonpath='{.items[0].metadata.name}')

    if [ -z "$PODS" ]; then
        log_warn "No pods found for green deployment, skipping smoke tests"
        return 0
    fi

    # Forward port
    kubectl port-forward "pod/$PODS" 8080:8080 -n "$NAMESPACE" &
    PF_PID=$!
    sleep 2

    # Run health check
    if curl -f http://localhost:8080/actuator/health/readiness &>/dev/null; then
        log_info "Smoke tests passed"
        kill $PF_PID 2>/dev/null || true
        return 0
    else
        log_error "Smoke tests failed"
        kill $PF_PID 2>/dev/null || true
        return 1
    fi
}

switch_traffic() {
    log_info "Switching traffic from blue to green..."

    GREEN_DEPLOY="${SERVICE_NAME}-green"

    # Update the service selector to point to green
    kubectl patch service "$SERVICE_NAME" -n "$NAMESPACE" -p '{"spec":{"selector":{"app":"'${GREEN_DEPLOY}'"}}}'

    # Wait a bit for connections to drain
    sleep 10

    log_info "Traffic switched to green deployment"
}

cleanup_blue() {
    log_info "Cleaning up blue deployment..."

    # Delete the old blue deployment
    kubectl delete deployment "$SERVICE_NAME" -n "$NAMESPACE" --ignore-not-found=true

    # Rename green to blue for next deployment
    kubectl get deployment "${SERVICE_NAME}-green" -n "$NAMESPACE" -o yaml | \
        sed "s/${SERVICE_NAME}-green/${SERVICE_NAME}/g" | \
        kubectl apply -f -

    kubectl delete deployment "${SERVICE_NAME}-green" -n "$NAMESPACE" --ignore-not-found=true

    log_info "Cleanup completed"
}

rollback() {
    log_error "Rolling back to previous deployment..."

    # Switch traffic back to blue
    kubectl patch service "$SERVICE_NAME" -n "$NAMESPACE" -p '{"spec":{"selector":{"app":"'${SERVICE_NAME}'"}}}'

    # Delete green deployment
    kubectl delete deployment "${SERVICE_NAME}-green" -n "$NAMESPACE" --ignore-not-found=true

    log_error "Rollback completed"
    exit 1
}

# Main execution
main() {
    log_info "Starting blue-green deployment for $SERVICE_NAME"
    log_info "Target image: $IMAGE"
    log_info "Namespace: $NAMESPACE"

    validate_inputs
    create_green_deployment

    if ! wait_for_ready "${SERVICE_NAME}-green"; then
        rollback
    fi

    if ! run_smoke_tests; then
        rollback
    fi

    switch_traffic
    cleanup_blue

    log_info "Blue-green deployment completed successfully!"
}

trap rollback ERR
main
