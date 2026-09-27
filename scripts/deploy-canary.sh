#!/bin/bash
set -euo pipefail

# Canary Deployment Script
# This script implements canary deployments with traffic gradual shifting
# Usage: ./deploy-canary.sh <service-name> <image:tag> <namespace> [canary-percentage]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_NAME="${1:?Service name required}"
IMAGE="${2:?Image tag required}"
NAMESPACE="${3:?Namespace required}"
CANARY_PERCENTAGE="${4:-10}"  # Start with 10% traffic to canary

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

validate_inputs() {
    if ! kubectl get namespace "$NAMESPACE" &>/dev/null; then
        log_error "Namespace $NAMESPACE does not exist"
        exit 1
    fi

    if ! kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" &>/dev/null; then
        log_error "Deployment $SERVICE_NAME not found"
        exit 1
    fi

    if ! [[ "$CANARY_PERCENTAGE" =~ ^[0-9]+$ ]] || [ "$CANARY_PERCENTAGE" -lt 1 ] || [ "$CANARY_PERCENTAGE" -gt 100 ]; then
        log_error "Canary percentage must be between 1 and 100"
        exit 1
    fi

    log_info "Inputs validated"
}

create_canary_deployment() {
    log_info "Creating canary deployment with $CANARY_PERCENTAGE% traffic..."

    CANARY_DEPLOY="${SERVICE_NAME}-canary"
    CURRENT_REPLICAS=$(kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')

    # Calculate canary replicas (at least 1)
    CANARY_REPLICAS=$((CURRENT_REPLICAS * CANARY_PERCENTAGE / 100))
    if [ $CANARY_REPLICAS -lt 1 ]; then
        CANARY_REPLICAS=1
    fi

    log_info "Current replicas: $CURRENT_REPLICAS"
    log_info "Canary replicas: $CANARY_REPLICAS"

    # Create canary deployment
    kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" -o yaml | \
        sed "s/$SERVICE_NAME/$CANARY_DEPLOY/g" | \
        sed "s/replicas: $CURRENT_REPLICAS/replicas: $CANARY_REPLICAS/g" | \
        sed "s|image:.*|image: $IMAGE|g" | \
        kubectl apply -f -

    log_info "Canary deployment created"
}

wait_for_canary_ready() {
    local timeout=300
    local elapsed=0

    log_info "Waiting for canary deployment to be ready..."

    while [ $elapsed -lt $timeout ]; do
        READY=$(kubectl get deployment "${SERVICE_NAME}-canary" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Progressing")].status}' 2>/dev/null || echo "False")

        if [ "$READY" == "True" ]; then
            log_info "Canary deployment is ready"
            return 0
        fi

        sleep 5
        elapsed=$((elapsed + 5))
        echo -n "."
    done

    log_error "Canary deployment failed to be ready"
    return 1
}

monitor_canary_metrics() {
    log_info "Monitoring canary metrics..."

    # This would integrate with your monitoring system
    # For now, just check basic health

    CANARY_PODS=$(kubectl get pods -n "$NAMESPACE" -l "app=${SERVICE_NAME}-canary" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null)

    if [ -z "$CANARY_PODS" ]; then
        log_error "No canary pods found"
        return 1
    fi

    for POD in $CANARY_PODS; do
        log_debug "Checking pod: $POD"

        # Check pod logs for errors
        if kubectl logs -n "$NAMESPACE" "$POD" --tail=50 | grep -i "error" | grep -v "404"; then
            log_error "Found errors in canary pod logs"
            return 1
        fi
    done

    log_info "Canary metrics look good"
    return 0
}

get_confirmation() {
    local prompt=$1
    read -p "$(echo -e ${YELLOW}$prompt${NC})" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        return 0
    else
        return 1
    fi
}

proceed_with_deployment() {
    log_info "Canary deployment is stable. Proceeding with full rollout..."

    CURRENT_REPLICAS=$(kubectl get deployment "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')
    CANARY_REPLICAS=$(kubectl get deployment "${SERVICE_NAME}-canary" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')

    # Scale down canary to 0
    kubectl scale deployment "${SERVICE_NAME}-canary" --replicas=0 -n "$NAMESPACE"

    # Update original deployment with new image
    kubectl set image deployment/"$SERVICE_NAME" "$SERVICE_NAME"="$IMAGE" -n "$NAMESPACE"

    # Wait for original deployment to update
    kubectl rollout status deployment/"$SERVICE_NAME" -n "$NAMESPACE" --timeout=5m

    # Clean up canary deployment
    kubectl delete deployment "${SERVICE_NAME}-canary" -n "$NAMESPACE" --ignore-not-found=true

    log_info "Full deployment completed successfully!"
}

rollback_deployment() {
    log_error "Rolling back canary deployment..."

    # Delete canary deployment
    kubectl delete deployment "${SERVICE_NAME}-canary" -n "$NAMESPACE" --ignore-not-found=true

    log_error "Canary deployment has been rolled back"
    exit 1
}

main() {
    log_info "Starting canary deployment for $SERVICE_NAME"
    log_info "Target image: $IMAGE"
    log_info "Namespace: $NAMESPACE"
    log_info "Initial canary traffic: $CANARY_PERCENTAGE%"

    validate_inputs
    create_canary_deployment

    if ! wait_for_canary_ready; then
        rollback_deployment
    fi

    # Monitor canary for a period
    log_info "Monitoring canary deployment for 5 minutes..."
    for i in {1..6}; do
        sleep 50
        if ! monitor_canary_metrics; then
            rollback_deployment
        fi
        log_info "Health check $i/6 passed"
    done

    # Ask for manual confirmation before proceeding
    if get_confirmation "Canary deployment stable. Proceed with full rollout? (y/n) "; then
        proceed_with_deployment
    else
        rollback_deployment
    fi
}

trap rollback_deployment ERR
main
