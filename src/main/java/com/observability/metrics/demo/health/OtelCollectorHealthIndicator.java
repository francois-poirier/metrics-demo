package com.observability.metrics.demo.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Custom health indicator that verifies the OpenTelemetry Collector is reachable.
 * Reports DOWN when the collector's health endpoint cannot be reached.
 */
@Component("otelCollector")
public class OtelCollectorHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OtelCollectorHealthIndicator.class);
    private static final String HEALTH_URL =
            System.getenv().getOrDefault("OTEL_COLLECTOR_HEALTH_URL", "http://localhost:13133");

    @Override
    public Health health() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(HEALTH_URL).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            int status = connection.getResponseCode();
            connection.disconnect();

            if (status == 200) {
                return Health.up()
                        .withDetail("url", HEALTH_URL)
                        .build();
            }
            return Health.down()
                    .withDetail("url", HEALTH_URL)
                    .withDetail("httpStatus", status)
                    .build();
        } catch (Exception e) {
            log.debug("OTel Collector health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", HEALTH_URL)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}

