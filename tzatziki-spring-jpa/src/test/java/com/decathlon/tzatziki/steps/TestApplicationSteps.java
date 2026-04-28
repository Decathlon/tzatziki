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

import java.net.Socket;
import java.util.List;
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
            new PostgreSQLContainer<>("postgres:16").withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            postgres.start();
            waitForPort(postgres.getHost(), postgres.getFirstMappedPort());
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword()
            ).applyTo(configurableApplicationContext.getEnvironment());
            SpringJPASteps.schemasToClean = List.of("public", "library", "store");
        }

        private static void waitForPort(String host, int port) {
            for (int i = 0; i < 30; i++) {
                try (Socket s = new Socket(host, port)) {
                    return;
                } catch (Exception e) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            throw new IllegalStateException("Port " + host + ":" + port + " not reachable after 15s");
        }
    }
}
