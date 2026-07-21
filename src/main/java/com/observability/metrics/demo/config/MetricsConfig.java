package com.observability.metrics.demo.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Registers custom application-level gauges and meters that go beyond the
 * default Spring Boot Actuator / Micrometer auto-configuration.
 */
@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry, ApplicationAvailability availability) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        Gauge.builder("demo.jvm.heap.usage.ratio", memoryBean,
                        bean -> {
                            var heap = bean.getHeapMemoryUsage();
                            return heap.getMax() > 0
                                    ? (double) heap.getUsed() / heap.getMax()
                                    : 0.0;
                        })
                .description("Ratio of used heap to max heap (0..1)")
                .register(registry);

        Gauge.builder("demo.application.ready", availability,
                        state -> state.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC ? 1.0 : 0.0)
                .description("Application readiness state as a numeric gauge (1=ready, 0=not ready)")
                .register(registry);
    }
}

