// == TestcontainersConfig.java ==
// Shared Testcontainers PostgreSQL configuration.
// Already present in src/test as TestcontainersConfiguration.java.
// Usage: @Import(TestcontainersConfiguration.class) on any integration test.

package com.example.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // @ServiceConnection auto-configures datasource, Flyway, JPA.
    // The container is started once and reused across test classes.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
    }
}
