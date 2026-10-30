package com.hassan.taskapi;

import com.hassan.taskapi.task.TaskDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 3: integration test. Starts the full application on a random port against a real
 * PostgreSQL and a real Redis, each in a throwaway Docker container (Testcontainers).
 *
 * This proves what mocks can't: Liquibase migrations run, Hibernate's schema validation passes,
 * JSON round-trips over real HTTP, and Redis caching/eviction actually work.
 *
 * Needs Docker. Runs during `mvn verify`, not `mvn test`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TaskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void createThenFetch_roundTripsThroughPostgres() {
        TaskDto created = createTask("Integration test", "real database");

        ResponseEntity<TaskDto> fetched = http.getForEntity("/api/tasks/{id}", TaskDto.class, created.id());

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().title()).isEqualTo("Integration test");
        assertThat(fetched.getBody().completed()).isFalse();
    }

    @Test
    void getById_putsTaskInRedis_andCompleteEvictsIt() {
        TaskDto created = createTask("Cache me", null);
        Cache tasksCache = cacheManager.getCache("tasks");
        assertThat(tasksCache).isNotNull();

        assertThat(tasksCache.get(created.id())).as("not cached before first read").isNull();

        http.getForEntity("/api/tasks/{id}", TaskDto.class, created.id());
        assertThat(tasksCache.get(created.id())).as("cached after first read").isNotNull();

        http.exchange("/api/tasks/{id}/complete", HttpMethod.PUT, null, TaskDto.class, created.id());
        assertThat(tasksCache.get(created.id())).as("evicted after update").isNull();

        TaskDto afterComplete = http.getForObject("/api/tasks/{id}", TaskDto.class, created.id());
        assertThat(afterComplete.completed()).as("no stale cached value served").isTrue();
    }

    @Test
    void missingTask_returns404() {
        ResponseEntity<String> response = http.getForEntity("/api/tasks/{id}", String.class, 999_999);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthEndpoint_isUp_withDatabaseAndRedis() {
        @SuppressWarnings("unchecked")
        Map<String, Object> health = http.getForObject("/actuator/health", Map.class);

        assertThat(health).containsEntry("status", "UP");
    }

    private TaskDto createTask(String title, String description) {
        Map<String, String> body = description == null
                ? Map.of("title", title)
                : Map.of("title", title, "description", description);

        ResponseEntity<TaskDto> response = http.postForEntity("/api/tasks", body, TaskDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
