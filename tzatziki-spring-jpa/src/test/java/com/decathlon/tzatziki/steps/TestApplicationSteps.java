package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.TestApplication;
import com.decathlon.tzatziki.utils.TypeParser;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class)
@ContextConfiguration(initializers = TestApplicationSteps.Initializer.class)
public class TestApplicationSteps {

    static {
        TypeParser.setDefaultPackage("com.decathlon");
    }

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:12").withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
    private static final PostgreSQLContainer<?> secondPostgres =
            new PostgreSQLContainer<>("postgres:12").withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            postgres.start();
            secondPostgres.start();
            TestPropertyValues.of(
                    "spring.datasource.jdbc-url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "spring.second-datasource.jdbc-url=" + secondPostgres.getJdbcUrl(),
                    "spring.second-datasource.username=" + secondPostgres.getUsername(),
                    "spring.second-datasource.password=" + secondPostgres.getPassword(),
                    "spring.flyway.enabled=false"
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
