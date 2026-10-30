package com.hassan.taskapi.info;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

/**
 * Shows which instance/container answered the request.
 * Behind a load balancer with 2+ tasks, refresh /api/info and watch the hostname change.
 */
@RestController
public class InfoController {

    private final String appVersion;
    private final Instant startedAt = Instant.now();

    public InfoController(@Value("${app.version:local}") String appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        return Map.of(
                "hostname", hostname(),
                "version", appVersion,
                "startedAt", startedAt.toString(),
                "javaVersion", System.getProperty("java.version"),
                "maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
