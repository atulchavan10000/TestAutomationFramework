#!/usr/bin/env bash

set -Eeuo pipefail

MAX_ATTEMPTS="${HEALTH_CHECK_ATTEMPTS:-20}"
DELAY_SECONDS="${HEALTH_CHECK_DELAY_SECONDS:-3}"

SERVICES=(
  "api-gateway|${SERVICES_API_GATEWAY_BASE_URL:-http://host.docker.internal:8000}"
  "user-service|${SERVICES_USER_SERVICE_BASE_URL:-http://host.docker.internal:8001}"
  "auth-service|${SERVICES_AUTH_SERVICE_BASE_URL:-http://host.docker.internal:8002}"
  "product-service|${SERVICES_PRODUCT_SERVICE_BASE_URL:-http://host.docker.internal:8003}"
  "order-service|${SERVICES_ORDER_SERVICE_BASE_URL:-http://host.docker.internal:8004}"
  "payment-service|${SERVICES_PAYMENT_SERVICE_BASE_URL:-http://host.docker.internal:8005}"
  "test-support-service|${SERVICES_TEST_SUPPORT_SERVICE_BASE_URL:-http://host.docker.internal:8006}"
)

unavailable_services=()

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  unavailable_services=()

  echo "Service health-check attempt ${attempt}/${MAX_ATTEMPTS}"

  for service_entry in "${SERVICES[@]}"; do
    IFS="|" read -r service_name service_base_url <<< "${service_entry}"
    health_url="${service_base_url%/}/health"

    if curl \
      --fail \
      --silent \
      --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --output /dev/null \
      "${health_url}"; then
      echo "  READY: ${service_name} (${health_url})"
    else
      echo "  WAITING: ${service_name} (${health_url})"
      unavailable_services+=("${service_name}|${health_url}")
    fi
  done

  if ((${#unavailable_services[@]} == 0)); then
    echo "All services are healthy."
    exit 0
  fi

  if ((attempt < MAX_ATTEMPTS)); then
    echo "Some services are unavailable. Retrying in ${DELAY_SECONDS} seconds."
    sleep "${DELAY_SECONDS}"
  fi
done

echo "Service health check failed. The following services are unavailable:"

for service_entry in "${unavailable_services[@]}"; do
  IFS="|" read -r service_name health_url <<< "${service_entry}"
  echo "  FAILED: ${service_name} (${health_url})"
done

exit 1