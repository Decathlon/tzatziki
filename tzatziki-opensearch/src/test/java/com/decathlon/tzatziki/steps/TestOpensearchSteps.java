package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.OpensearchApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.utility.DockerImageName;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = OpensearchApplication.class)
@ContextConfiguration(initializers = TestOpensearchSteps.Initializer.class)
@ActiveProfiles({"test"})
@Slf4j
public class TestOpensearchSteps {

    public static OpenSearchContainer<?> opensearch = new OpenSearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.11.0"))
            .withEnv("action.auto_create_index", "false");

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            opensearch.start();
            TestPropertyValues.of(
                    "opensearch.host=" + opensearch.getHttpHostAddress()
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @PreDestroy
    public void stopOpensearch() {
        if (opensearch != null && opensearch.isRunning()) {
            opensearch.stop();
        }
    }
}
