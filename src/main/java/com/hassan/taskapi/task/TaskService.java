package com.hassan.taskapi.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> findAll() {
        return repository.findAll().stream().map(TaskDto::from).toList();
    }

    /**
     * Cache-aside: the first call hits PostgreSQL and logs "Cache miss".
     * Repeat calls are served from Redis, so the log line does not appear again.
     */
    @Cacheable(cacheNames = "tasks", key = "#id")
    @Transactional(readOnly = true)
    public TaskDto findById(Long id) {
        log.info("Cache miss for task {} - reading from database", id);
        return repository.findById(id)
                .map(TaskDto::from)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public TaskDto create(CreateTaskRequest request) {
        Task saved = repository.save(new Task(request.title(), request.description()));
        log.info("Created task {}", saved.getId());
        return TaskDto.from(saved);
    }

    @CacheEvict(cacheNames = "tasks", key = "#id")
    @Transactional
    public TaskDto complete(Long id) {
        Task task = repository.findById(id).orElseThrow(() -> notFound(id));
        task.markCompleted();
        return TaskDto.from(task);
    }

    @CacheEvict(cacheNames = "tasks", key = "#id")
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found");
    }
}
