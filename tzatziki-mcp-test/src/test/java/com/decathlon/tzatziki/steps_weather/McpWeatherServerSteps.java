package com.decathlon.tzatziki.steps_weather;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import com.decathlon.tzatziki.mcp.http.server.McpWeatherServer;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static com.decathlon.tzatziki.utils.HttpUtils.url;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = McpWeatherServer.class)
@ContextConfiguration(initializers = McpWeatherServerSteps.Initializer.class)
@Slf4j
public class McpWeatherServerSteps {
    @LocalServerPort
    private Integer serverPort;

    @Before(order = -1)
    public void before() {
        McpClientConfiguration.setMcpClientTransport(HttpClientStreamableHttpTransport
                .builder("http://localhost:" + serverPort).build());

    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "weather.api.base-url=" + url() + "/weather"
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
