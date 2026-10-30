package com.hassan.taskapi.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Layer 1: pure unit tests. No Spring, no database, runs in milliseconds.
 * Tests the business rules in TaskService with a mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void findById_returnsTask_whenItExists() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Task("Write tests", "unit layer")));

        TaskDto result = service.findById(1L);

        assertThat(result.title()).isEqualTo("Write tests");
        assertThat(result.completed()).isFalse();
    }

    @Test
    void findById_throws404_whenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.findById(99L));

        assertThat(ex.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void create_savesTaskAsNotCompleted() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskDto result = service.create(new CreateTaskRequest("Deploy", "to ECS"));

        assertThat(result.title()).isEqualTo("Deploy");
        assertThat(result.description()).isEqualTo("to ECS");
        assertThat(result.completed()).isFalse();
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void complete_marksTaskCompleted() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Task("Finish step 4", null)));

        TaskDto result = service.complete(1L);

        assertThat(result.completed()).isTrue();
    }

    @Test
    void delete_throws404_andDeletesNothing_whenMissing() {
        when(repository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).deleteById(anyLong());
    }
}
