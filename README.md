# Metrics Demo — Spring Boot + OpenTelemetry + VictoriaMetrics

A self-contained Proof-of-Concept that demonstrates **metrics, traces, and logs**
exported from a Spring Boot 4 application via the OpenTelemetry Collector into the
VictoriaMetrics stack, with Grafana for visualisation.

## Architecture

```
┌──────────────┐      OTLP/HTTP       ┌──────────────────┐
│  Spring Boot │ ──────────────────▶  │  OTel Collector  │
│  Application │   :4318              │                  │
└──────────────┘                      └──────┬───────────┘
                                             │
              ┌──────────────────────────────┼──────────────────────────────┐
              ▼                              ▼                              ▼
     ┌────────────────┐             ┌─────────────────┐             ┌────────────────┐
     │ VictoriaMetrics│             │ VictoriaTraces  │             │ Logs Backends  │
     │  (metrics)     │             │  (traces)       │             │ (dual export)  │
     │  :8428         │             │  :9429          │             │                │
     └───────┬────────┘             └────────┬────────┘             │ VictoriaLogs   │
             │                               │                      │  :9428         │
             │                               │                      │ Loki           │
             │                               │                      │  :3100         │
             │                               │                      └───────┬────────┘
             └───────────────────────────────┼──────────────────────────────┘
                                             ▼
                                    ┌─────────────────┐
                                    │     Grafana     │
                                    │     :3000       │
                                    └─────────────────┘
```

The collector exports logs to **both VictoriaLogs and Loki**. Loki is included for
Grafana Logs Drilldown compatibility, while VictoriaLogs remains the primary logs store.

## Prerequisites

- **Docker & Docker Compose** (v2+) or **Podman** with `podman-compose`
- **Java 21** (only needed for local development outside Docker)
- **Maven** (or use the included `mvnw` wrapper)

## Quick Start

```bash
# 1. Build the Spring Boot application (skip tests for speed)
mvn clean package -DskipTests

# 2. Start the full stack (builds the app + all observability services)
podman compose up -d --build      # or: docker compose up -d --build

# 3. Open Grafana
open http://localhost:3000        # admin / admin123

# 4. Generate load to populate dashboards
./load-generator.sh
```

### Running the App Locally (without Docker)

```bash
# Start only the observability stack
podman compose up -d victoria-metrics victoria-logs victoria-traces loki otel-collector grafana

# Run the Spring Boot application
./mvnw spring-boot:run
```

## API Endpoints

| Method | Path                       | Description                                |
|--------|----------------------------|--------------------------------------------|
| GET    | `/api/hello`               | Simple greeting with timestamp             |
| GET    | `/api/data`                | Fetches simulated data (observed + timed)  |
| POST   | `/api/process`             | Processes input (`{"value":"..."}`)        |
| GET    | `/api/items?count=N`       | Returns N items; latency scales with count |
| GET    | `/api/slow-operation?ms=N` | Sleeps for N ms to simulate slow work      |
| GET    | `/api/flaky?rate=0.3`      | Randomly fails at the given rate (0–1)     |
| GET    | `/api/error`               | Always throws a 500 (for dashboard tests)  |
| GET    | `/api/health/custom`       | Simple custom health check                 |
| GET    | `/actuator/health`         | Spring Boot health (incl. OTel Collector)  |
| GET    | `/actuator/metrics`        | All registered Micrometer metrics          |

## Custom Metrics

| Metric                       | Type    | Description                        |
|------------------------------|---------|------------------------------------|
| `demo.fetch.total`           | Counter | Total data-fetch invocations       |
| `demo.fetch.duration`        | Timer   | Duration of data-fetch operations  |
| `demo.process.total`         | Counter | Total processing invocations       |
| `demo.active.requests`       | Gauge   | Currently in-flight requests       |
| `demo.errors`                | Counter | Errors by exception type & status  |
| `demo.jvm.heap.usage.ratio`  | Gauge   | Used heap / max heap (0..1)        |

In addition, all `@Observed`-annotated service methods automatically produce
**observation timers and span traces** via Micrometer Observation + OpenTelemetry.

## Load Generator

The `load-generator.sh` script cycles through **four phases** to create realistic
traffic patterns that are clearly visible on the Grafana dashboards:

| Phase        | Duration  | Behaviour                                         |
|--------------|-----------|---------------------------------------------------|
| **Warm-up**  | ~15 s     | Steady happy-path calls (`/hello`, `/data`, `/items?count=5`) |
| **Normal**   | ~20 s     | Mixed endpoints, variable item counts, 10 % flaky rate       |
| **Stress**   | ~8 s      | Burst traffic, large payloads, 50 % failure rate, deliberate errors |
| **Recovery** | ~20 s     | Low volume, minimal errors — system stabilises    |

After the fourth phase the cycle restarts, producing a repeating sawtooth pattern
that is easy to spot in time-series graphs.

```bash
# Start the load generator (runs until Ctrl+C)
./load-generator.sh

# Optionally point it at a different host
./load-generator.sh http://my-host:8080
```

---

## Grafana Demo — Step-by-Step Guide

### Step 1: Open Grafana

Navigate to **http://localhost:3000** in your browser.

- **Username:** `admin`
- **Password:** `admin123`

> If prompted to change the password, you can skip or set a new one.

---

### Step 2: Start the Load Generator

Open a **separate terminal** and run:

```bash
./load-generator.sh
```

Leave it running — it continuously sends traffic through all four phases.

---

### Step 3: Explore the "Spring Boot Application Metrics" Dashboard

1. Click the **hamburger menu (☰)** → **Dashboards**.
2. Open **"Spring Boot Application Metrics"**.

**What to look for:**

| Panel                       | What it shows                                               |
|-----------------------------|-------------------------------------------------------------|
| HTTP Request Rate           | Requests/sec — watch it spike during the *Stress* phase     |
| HTTP Response Time (p95/p99)| Latency percentiles — `/slow-operation` and large `/items` counts push these up |
| HTTP Status Codes           | 2xx vs 5xx split — errors appear during Stress              |
| JVM Heap Usage              | Memory consumption over time                                |
| JVM Threads                 | Active thread count                                         |

**Demo talking points:**
- During **Warm-up** → flat, low request rate, near-zero errors.
- During **Stress** → request rate doubles, p95 latency jumps (slow ops + large item lists), 5xx errors spike.
- During **Recovery** → metrics settle back to baseline within seconds.

---

### Step 4: Explore the "Application Reliability & SLOs" Dashboard

1. Go back to **Dashboards** → open **"Application Reliability & SLOs"**.

**What to look for:**

| Panel               | What it shows                                            |
|---------------------|----------------------------------------------------------|
| Error Rate (%)      | Percentage of failed requests over time                  |
| Availability        | Uptime ratio — dips when `/error` and `/flaky` fire      |
| Error Breakdown     | Errors grouped by exception type and HTTP status         |
| Application Logs    | Live log stream from the app (via VictoriaLogs / Loki)   |

**Demo talking points:**
- Error rate clearly correlates with the *Stress* phase (50 % flaky + deliberate 500s).
- Availability recovers as soon as the Recovery phase begins.
- Click on a log line to see structured fields (traceId, spanId, level).

---

### Step 5: Drill Down into Traces

1. In any dashboard panel, click a data point and choose **"View trace"** (if available), OR
2. Go to **☰ → Explore** → select the **VictoriaTraces** datasource.
3. Search for traces by service name: `metrics-demo`.

**What to look for:**
- Each request to `/api/data` or `/api/process` generates a full trace with spans for the service-layer `@Observed` methods.
- Slow spans (from `/api/slow-operation`) are easy to spot by duration.
- Error spans are marked in red.

---

### Step 6: Query Logs in Explore

1. Go to **☰ → Explore** → select the **Loki** or **VictoriaLogs** datasource.
2. Run a query:
   - **Loki:** `{service_name="metrics-demo"} |= "ERROR"`
   - **VictoriaLogs:** `service_name:metrics-demo AND level:ERROR`
3. You'll see all error log lines generated by the `/api/error` and `/api/flaky` endpoints.

> **Log levels:** The OpenTelemetry Collector is configured with a `transform/logs`
> processor that copies `severity_text` to a `level` attribute, ensuring
> VictoriaLogs displays proper log levels (INFO, WARN, ERROR) instead of `UNK`.

**Demo talking points:**
- Logs are automatically correlated with traces via `traceId`.
- Click the trace ID link in a log line to jump directly to the corresponding trace.

---

### Step 7: Query Metrics in Explore

1. Go to **☰ → Explore** → select the **VictoriaMetrics** datasource.
2. Try these PromQL queries:

```promql
# Request rate by endpoint
rate(http_server_requests_seconds_count[1m])

# p95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# Custom counter — total data fetches
rate(demo_fetch_total[1m])

# Error counter by exception type
rate(demo_errors_total[1m])

# JVM heap usage ratio
demo_jvm_heap_usage_ratio
```

---

### Step 8: Observe the Full Cycle

Let the load generator run for **2–3 full cycles** (~3 minutes). Then look at the
dashboards with a **Last 5 minutes** time range to see the repeating sawtooth pattern:

- 📈 Request rate ramps up → spikes → drops
- 📉 Error rate pulses during stress → returns to near-zero
- ⏱️ Latency p95 swings from ~50 ms (warm-up) to 2–3 s (stress)

This clearly demonstrates how observability tooling surfaces application behaviour
in real time.

---

## Grafana Dashboards

Two provisioned dashboards are available out of the box:

1. **Spring Boot Application Metrics** — request rates, latencies, JVM stats
2. **Application Reliability & SLOs** — error rate, availability, logs panel

## Grafana Alert Rules

Grafana now auto-provisions reliability alerts from
`observability/grafana/provisioning/alerting/alert-rules.yml`.

The current baseline focuses on the production signals you listed:

| Alert | Severity | Threshold |
|-------|----------|-----------|
| Application Down | Critical | readiness < 1 for 2m |
| HTTP 5xx Rate > 5% | Critical | 5xx / total requests > 5% for 5m |
| JVM Heap > 95% | Critical | heap usage ratio > 95% for 3m |
| DB Pool > 90% | Critical | active / max connections > 90% for 5m |
| Thread Pool > 90% | Critical | busy / current Tomcat threads > 90% for 5m |
| Heap > 85% | Warning | heap usage ratio > 85% for 5m |
| p95 Latency > 1s | Warning | p95 request latency > 1s for 5m |
| CPU > 80% | Warning | process CPU usage > 80% for 5m |
| No Traffic | Warning | request rate < 0.01 rps for 10m |

Notes:

- `Application Down` is backed by the new `demo.application.ready` gauge.
- `CPU > 80%` uses Micrometer's standard `process.cpu.usage` metric.
- `DB Pool > 90%` and `Thread Pool > 90%` are provisioned with common Micrometer/Tomcat metric names and will become active once those meters are present.

After updating alert files, restart Grafana to reload provisioning:

```bash
docker compose restart grafana
# or
podman compose restart grafana
```

### Datasources

| Name                             | Type                    | Purpose                          |
|----------------------------------|-------------------------|----------------------------------|
| VictoriaMetrics                  | victoriametrics-metrics | Primary metrics datasource       |
| VictoriaMetrics (Prometheus API) | prometheus              | Grafana Drilldown compatibility  |
| VictoriaLogs                     | victoriametrics-logs    | Primary logs datasource          |
| Loki                             | loki                    | Grafana Logs Drilldown compat.   |
| VictoriaTraces                   | jaeger                  | Primary traces datasource        |
| VictoriaTraces (Tempo API)       | tempo                   | Grafana Traces Drilldown compat. |

## Project Structure

```
├── docker-compose.yml              # Full stack definition
├── Dockerfile                      # Multi-stage build for the Spring Boot app
├── load-generator.sh               # Phased traffic generator (4 phases per cycle)
├── observability/
│   ├── grafana/provisioning/       # Datasources, dashboards, and alert rules
│   ├── loki/loki-config.yml        # Loki configuration
│   └── otel-collector/             # OTel Collector pipeline config
├── src/main/java/.../
│   ├── MetricsDemoApplication.java
│   ├── OpenTelemetryAppenderInitializer.java
│   ├── config/MetricsConfig.java          # Custom gauges
│   ├── controller/
│   │   ├── DemoController.java            # REST endpoints
│   │   └── GlobalExceptionHandler.java    # Centralised error handling
│   ├── health/OtelCollectorHealthIndicator.java
│   └── service/DemoService.java           # Business logic with @Observed
└── src/main/resources/application.yml     # App + telemetry configuration
```

## OpenTelemetry Collector Configuration

The collector config (`observability/otel-collector/otel-collector-config.yml`) defines
how telemetry flows from the application to the storage backends.

### Receivers

```yaml
receivers:
  otlp:
    protocols:
      grpc: { endpoint: 0.0.0.0:4317 }
      http: { endpoint: 0.0.0.0:4318 }
```

Accepts OTLP data over gRPC (`:4317`) and HTTP (`:4318`). The Spring Boot app sends
metrics, traces, and logs to the HTTP endpoint.

### Processors

| Processor          | Purpose                                                      |
|--------------------|--------------------------------------------------------------|
| `memory_limiter`   | Prevents OOM by dropping data when memory exceeds thresholds |
| `batch`            | Batches telemetry for efficient export                       |
| `transform/logs`   | Copies `severity_text` → `level` attribute for VictoriaLogs  |

The `transform/logs` processor ensures VictoriaLogs displays proper log levels
(INFO, WARN, ERROR) instead of `UNK`:

```yaml
transform/logs:
  log_statements:
    - context: log
      statements:
        - set(attributes["level"], severity_text)
```

### Exporters

| Exporter             | Destination                     | Signal  |
|----------------------|---------------------------------|---------|
| `otlp_http/metrics`  | VictoriaMetrics `:8428`         | Metrics |
| `otlp_http/traces`   | VictoriaTraces `:9428`          | Traces  |
| `otlp_http/logs`     | VictoriaLogs `:9428`            | Logs    |
| `otlp_http/loki_logs`| Loki `:3100` (Grafana compat.)  | Logs    |

### Pipelines

Each signal type has its own pipeline wiring receivers → processors → exporters:

```yaml
pipelines:
  metrics:  [otlp] → [memory_limiter, batch] → [otlp_http/metrics]
  traces:   [otlp] → [memory_limiter, batch] → [otlp_http/traces]
  logs:     [otlp] → [memory_limiter, transform/logs, batch] → [otlp_http/logs, otlp_http/loki_logs]
```

Logs are exported to **both** VictoriaLogs and Loki for Grafana Logs Drilldown compatibility.

## Stopping Everything

```bash
podman compose down -v   # -v removes named volumes (data will be lost)
# or
docker compose down -v
```
