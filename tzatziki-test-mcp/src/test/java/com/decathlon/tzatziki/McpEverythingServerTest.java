package com.decathlon.tzatziki;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

/**
 * Test runner for MCP Everything Server feature tests.
 * This test suite runs all scenarios against the everything server in Docker.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/mcp-everything-server.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-everything.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.decathlon.tzatziki.steps, com.decathlon.tzatziki.steps_everything")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
public class McpEverythingServerTest {
}

