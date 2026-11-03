package com.fatoora.invoicing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real dependencies in Docker for tests:
 * <ul>
 *   <li>PostgreSQL 16 (same version as production), wired via {@code @ServiceConnection}</li>
 *   <li>Mailpit, a real SMTP server whose HTTP API lets tests read what was sent</li>
 * </ul>
 * Spring caches the test context, so all integration tests share these containers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27")).withExposedPorts(1025, 8025);
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
            registry.add("test.mailpit.api", () -> "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
        };
    }
}
