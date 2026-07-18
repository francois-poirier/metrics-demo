#!/usr/bin/env bash
# --------------------------------------------------------------------------
# load-generator.sh — Sends continuous traffic to the demo app so that
# metrics, traces, and logs are populated in the dashboards.
#
# The script runs through repeating phases to create interesting patterns
# in Grafana dashboards:
#   Phase 1 – Warm-up:       steady, low-volume happy-path traffic
#   Phase 2 – Normal load:   mixed endpoints, moderate concurrency
#   Phase 3 – Stress:        high item counts, slow ops, elevated error rate
#   Phase 4 – Recovery:      back to normal, errors subside
#
# Usage:
#   ./load-generator.sh                        # run all phases in a loop
#   ./load-generator.sh --phase warmup         # run only the warm-up phase
#   ./load-generator.sh --phase stress         # run only the stress phase
#   ./load-generator.sh --phase normal --iterations 5
#   ./load-generator.sh --url http://myhost:8080 --phase recovery
#
# Phases: warmup, normal, stress, recovery, all (default)
# --------------------------------------------------------------------------
set -euo pipefail

# ── Parse arguments ──────────────────────────────────────────────────────────
BASE_URL="http://localhost:8080"
PHASE="all"
ITERATIONS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --phase|-p)   PHASE="$2"; shift 2 ;;
    --url|-u)     BASE_URL="$2"; shift 2 ;;
    --iterations|-n) ITERATIONS="$2"; shift 2 ;;
    --help|-h)
      echo "Usage: ./load-generator.sh [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --url, -u URL          Target base URL (default: http://localhost:8080)"
      echo "  --phase, -p PHASE      Phase to run: warmup, normal, stress, recovery, all (default: all)"
      echo "  --iterations, -n N     Number of iterations for the selected phase"
      echo "  --help, -h             Show this help message"
      echo ""
      echo "Examples:"
      echo "  ./load-generator.sh                          # all phases in a loop"
      echo "  ./load-generator.sh --phase stress           # only stress phase"
      echo "  ./load-generator.sh -p warmup -n 5           # 5 iterations of warm-up"
      echo "  ./load-generator.sh -u http://app:8080 -p normal"
      exit 0
      ;;
    *)            BASE_URL="$1"; shift ;;  # backwards-compatible positional arg
  esac
done

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}  📊 Metrics Demo — Load Generator${NC}"
echo -e "${CYAN}  Target: ${BASE_URL}${NC}"
echo -e "${CYAN}  Press Ctrl+C to stop${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Helper: call endpoint and print result
call() {
  local method="$1" path="$2" iteration="$3"
  shift 3
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" "$@" "${BASE_URL}${path}")
  if [[ "${http_code}" == 2* ]]; then
    echo -e "  [${iteration}] ${GREEN}${method} ${path} → ${http_code}${NC}"
  elif [[ "${http_code}" == 5* ]]; then
    echo -e "  [${iteration}] ${RED}${method} ${path} → ${http_code}${NC}"
  else
    echo -e "  [${iteration}] ${YELLOW}${method} ${path} → ${http_code}${NC}"
  fi
}

# ──────────────────────────────────────────────────────────────────────────────
# Phase functions
# ──────────────────────────────────────────────────────────────────────────────

phase_warmup() {
  local iterations="${1:-10}"
  echo -e "\n${CYAN}▶ Phase 1: WARM-UP (${iterations} iterations, 1.5s delay)${NC}"
  echo -e "  Steady happy-path traffic to establish baseline metrics.\n"
  for i in $(seq 1 "$iterations"); do
    call "GET " "/api/hello" "$i"
    call "GET " "/api/data" "$i"
    call "POST" "/api/process" "$i" \
         -X POST -H "Content-Type: application/json" -d "{\"value\":\"warmup-${i}\"}"
    call "GET " "/api/items?count=5" "$i"
    call "GET " "/api/health/custom" "$i"
    sleep 1.5
  done
}

phase_normal() {
  local iterations="${1:-20}"
  echo -e "\n${CYAN}▶ Phase 2: NORMAL LOAD (${iterations} iterations, 1s delay)${NC}"
  echo -e "  Mixed endpoints with moderate variation.\n"
  for i in $(seq 1 "$iterations"); do
    call "GET " "/api/hello" "$i"
    call "GET " "/api/data" "$i"

    # Variable item count (5–50)
    local count=$(( (RANDOM % 46) + 5 ))
    call "GET " "/api/items?count=${count}" "$i"

    # Process some data
    call "POST" "/api/process" "$i" \
         -X POST -H "Content-Type: application/json" -d "{\"value\":\"batch-${i}-count-${count}\"}"

    # Occasional slow operation (every 4th request, 500–1500ms)
    if (( i % 4 == 0 )); then
      local ms=$(( (RANDOM % 1000) + 500 ))
      call "GET " "/api/slow-operation?ms=${ms}" "$i"
    fi

    # Low failure-rate flaky calls (10%)
    call "GET " "/api/flaky?rate=0.1" "$i"

    sleep 1
  done
}

phase_stress() {
  local iterations="${1:-15}"
  echo -e "\n${RED}▶ Phase 3: STRESS (${iterations} iterations, 0.5s delay)${NC}"
  echo -e "  High load, large payloads, elevated error rates.\n"
  for i in $(seq 1 "$iterations"); do
    # Rapid hello calls (simulates burst)
    call "GET " "/api/hello" "$i"
    call "GET " "/api/hello" "$i"

    # Large item lists (100–500)
    local count=$(( (RANDOM % 400) + 100 ))
    call "GET " "/api/items?count=${count}" "$i"

    # Heavy processing
    call "POST" "/api/process" "$i" \
         -X POST -H "Content-Type: application/json" \
         -d "{\"value\":\"stress-test-payload-${i}-$(date +%s)\"}"

    # Slow operations (1500–3000ms)
    local ms=$(( (RANDOM % 1500) + 1500 ))
    call "GET " "/api/slow-operation?ms=${ms}" "$i"

    # High failure-rate flaky calls (50%)
    call "GET " "/api/flaky?rate=0.5" "$i"
    call "GET " "/api/flaky?rate=0.5" "$i"

    # Deliberate errors
    call "GET " "/api/error" "$i"

    # Fetch data under load
    call "GET " "/api/data" "$i"

    sleep 0.5
  done
}

phase_recovery() {
  local iterations="${1:-10}"
  echo -e "\n${GREEN}▶ Phase 4: RECOVERY (${iterations} iterations, 2s delay)${NC}"
  echo -e "  Traffic subsides, errors drop, system stabilises.\n"
  for i in $(seq 1 "$iterations"); do
    call "GET " "/api/hello" "$i"
    call "GET " "/api/data" "$i"
    call "GET " "/api/items?count=10" "$i"
    call "POST" "/api/process" "$i" \
         -X POST -H "Content-Type: application/json" -d "{\"value\":\"recovery-${i}\"}"

    # Very low failure rate (5%)
    call "GET " "/api/flaky?rate=0.05" "$i"

    sleep 2
  done
}

# ──────────────────────────────────────────────────────────────────────────────
# Main — run selected phase or cycle through all
# ──────────────────────────────────────────────────────────────────────────────

run_single_phase() {
  local phase="$1"
  local iters="${ITERATIONS}"
  case "$phase" in
    warmup)   phase_warmup  "${iters:-10}" ;;
    normal)   phase_normal  "${iters:-20}" ;;
    stress)   phase_stress  "${iters:-15}" ;;
    recovery) phase_recovery "${iters:-10}" ;;
    *)
      echo -e "${RED}Unknown phase: ${phase}${NC}"
      echo "Valid phases: warmup, normal, stress, recovery, all"
      exit 1
      ;;
  esac
}

if [[ "$PHASE" != "all" ]]; then
  # Single phase mode — run once and exit
  echo -e "${CYAN}  Running single phase: ${PHASE}${NC}"
  run_single_phase "$PHASE"
  echo -e "\n${GREEN}  ✅ Phase '${PHASE}' complete.${NC}"
else
  # Full cycle mode — loop forever
  cycle=0
  while true; do
    cycle=$((cycle + 1))
    echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  🔁 Cycle ${cycle} starting...${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    phase_warmup 10
    phase_normal 20
    phase_stress 15
    phase_recovery 10

    echo -e "\n${GREEN}  ✅ Cycle ${cycle} complete. Restarting in 5 seconds...${NC}"
    sleep 5
  done
fi
