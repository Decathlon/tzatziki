package com.decathlon.tzatziki;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import io.cucumber.picocontainer.PicoFactory;
import org.junit.runner.RunWith;

/**
 * Test runner for MCP Everything Server feature tests.
 * This test suite runs all scenarios against the everything server in Docker.
 */
@RunWith(Cucumber.class)
@CucumberOptions(
        plugin = {"pretty", "json:target/cucumber-everything.json"},
        features = "classpath:features/mcp-everything-server.feature",
        glue = {"com.decathlon.tzatziki.steps", "com.decathlon.tzatziki.steps_everything"},
        objectFactory = PicoFactory.class
)
public class McpEverythingServerTest {
}

