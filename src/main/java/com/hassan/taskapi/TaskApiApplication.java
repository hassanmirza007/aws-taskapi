package com.hassan.taskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Keep this class free of feature annotations like @EnableCaching.
 * Test slices (@WebMvcTest) load this class, and anything on it gets dragged into every slice.
 * Feature config lives in the config package, which slices skip.
 */
@SpringBootApplication
public class TaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
