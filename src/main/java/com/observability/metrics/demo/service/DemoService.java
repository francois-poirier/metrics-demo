package com.observability.metrics.demo.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final Counter fetchCounter;
    private final Counter processCounter;
    private final Timer fetchTimer;
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public DemoService(MeterRegistry registry) {
        this.fetchCounter = Counter.builder("demo.fetch.total")
                .description("Total number of data fetches")
                .register(registry);

        this.processCounter = Counter.builder("demo.process.total")
                .description("Total number of data processing calls")
                .tag("type", "default")
                .register(registry);

        this.fetchTimer = Timer.builder("demo.fetch.duration")
                .description("Duration of data fetch operations")
                .register(registry);

        // Gauge that tracks in-flight requests
        registry.gauge("demo.active.requests", activeRequests);
    }

    @Observed(name = "demo.fetch-data", contextualName = "fetch-data")
    public String fetchData() {
        activeRequests.incrementAndGet();
        try {
            return fetchTimer.record(() -> {
                fetchCounter.increment();
                log.info("Fetching data from simulated data source");
                sleep(100 + ThreadLocalRandom.current().nextInt(200));
                return "Sample data from database";
            });
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    @Observed(name = "demo.process-data", contextualName = "process-data")
    public String processData(String input) {
        activeRequests.incrementAndGet();
        try {
            processCounter.increment();
            log.info("Processing data: {}", input);

            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("Input must not be empty");
            }

            sleep(150 + ThreadLocalRandom.current().nextInt(250));
            return "Processed: " + input.toUpperCase();
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    @Observed(name = "demo.slow-operation", contextualName = "slow-operation")
    public void slowOperation(long durationMs) {
        log.info("Starting slow operation for {} ms", durationMs);
        sleep(durationMs);
        log.info("Slow operation completed");
    }

    /**
     * Simulates a flaky operation that fails randomly at the configured rate.
     * Useful for testing error-rate dashboards and alerting.
     *
     * @param failureRate probability of failure (0.0 – 1.0)
     * @return a success message or throws
     */
    @Observed(name = "demo.flaky-operation", contextualName = "flaky-operation")
    public String flakyOperation(double failureRate) {
        sleep(50 + ThreadLocalRandom.current().nextInt(150));
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.warn("Flaky operation failed (rate={})", failureRate);
            throw new RuntimeException("Simulated transient failure");
        }
        log.info("Flaky operation succeeded");
        return "OK";
    }

    /**
     * Returns a list of items with simulated latency proportional to the count.
     */
    @Observed(name = "demo.list-items", contextualName = "list-items")
    public List<String> listItems(int count) {
        log.info("Listing {} items", count);
        sleep(10L * count + ThreadLocalRandom.current().nextInt(50));
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> "item-" + i)
                .toList();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}