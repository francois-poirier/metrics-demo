package com.observability.metrics.demo.controller;

import com.observability.metrics.demo.service.DemoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello from Metrics Demo!",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/data")
    public Map<String, Object> getData() {
        return Map.of(
                "data", demoService.fetchData(),
                "status", "success"
        );
    }

    @PostMapping("/process")
    public Map<String, Object> processData(@RequestBody Map<String, String> request) {
        String value = request.getOrDefault("value", "");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("input", value);
        response.put("result", demoService.processData(value));
        response.put("status", "processed");
        return response;
    }

    /**
     * Slow operation whose duration can be tuned via query param.
     * Example: GET /api/slow-operation?ms=3000
     */
    @GetMapping("/slow-operation")
    public Map<String, Object> slowOperation(@RequestParam(defaultValue = "2000") long ms) {
        demoService.slowOperation(ms);
        return Map.of(
                "message", "Operation completed",
                "duration_ms", ms
        );
    }

    /**
     * Simulates a flaky endpoint.  The failure rate is configurable so you can
     * watch error-rate dashboards react in real time.
     * Example: GET /api/flaky?rate=0.3
     */
    @GetMapping("/flaky")
    public Map<String, Object> flaky(@RequestParam(defaultValue = "0.3") double rate) {
        String result = demoService.flakyOperation(rate);
        return Map.of("result", result, "requestedFailureRate", rate);
    }

    /**
     * Returns a list of items.  Latency scales with count so you can observe
     * percentile histograms change shape.
     * Example: GET /api/items?count=50
     */
    @GetMapping("/items")
    public Map<String, Object> items(@RequestParam(defaultValue = "10") int count) {
        if (count < 1 || count > 1000) {
            throw new IllegalArgumentException("count must be between 1 and 1000");
        }
        return Map.of(
                "items", demoService.listItems(count),
                "count", count
        );
    }

    @GetMapping("/error")
    public Map<String, Object> errorEndpoint() {
        throw new RuntimeException("Simulated error for testing");
    }

    @GetMapping("/health/custom")
    public Map<String, String> customHealth() {
        return Map.of("status", "UP", "service", "metrics-demo");
    }
}