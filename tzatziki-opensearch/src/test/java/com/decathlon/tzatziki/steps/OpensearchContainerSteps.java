package com.decathlon.tzatziki.steps;

import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class OpensearchContainerSteps {

    private static OpensearchContainer<?> opensearch;

    @Before(order = -1)
    public void startOpenSearchContainer() {
        if (opensearch == null) {
            opensearch = new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.11.0"))
                    .withEnv("action.auto_create_index", "false");
            opensearch.start();

            String hostAddress = opensearch.getHttpHostAddress();
            System.setProperty("opensearch.host", hostAddress);
            log.info("OpenSearch container started at: {}", hostAddress);
        }
    }

    public static void stopContainer() {
        if (opensearch != null && opensearch.isRunning()) {
            opensearch.stop();
            opensearch = null;
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(OpensearchContainerSteps::stopContainer));
    }
}
