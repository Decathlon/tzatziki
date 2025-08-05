package com.decathlon.tzatziki.steps;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.ServerSocket;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test to verify that port configuration works in practice.
 * This test manually creates WireMock servers to validate the configuration logic.
 */
public class PortConfigurationIntegrationTest {

    private String originalPortProperty;

    @BeforeEach
    void setUp() {
        // Save original system property if it exists
        originalPortProperty = System.getProperty("tzatziki.http.port");
    }

    @AfterEach
    void tearDown() {
        // Restore original system property
        if (originalPortProperty != null) {
            System.setProperty("tzatziki.http.port", originalPortProperty);
        } else {
            System.clearProperty("tzatziki.http.port");
        }
    }

    @Test
    void testWireMockWithSpecificPort() throws IOException {
        // Find an available port
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // Create WireMock server with specific port using the same configuration logic as HttpSteps
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
                .port(port);

        WireMockServer server = new WireMockServer(config);
        try {
            server.start();
            assertThat(server.port()).isEqualTo(port);
        } finally {
            server.stop();
        }
    }

    @Test
    void testWireMockWithDynamicPort() {
        // Create WireMock server with dynamic port using the same configuration logic as HttpSteps
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
                .dynamicPort();

        WireMockServer server = new WireMockServer(config);
        try {
            server.start();
            assertThat(server.port()).isGreaterThan(0);
        } finally {
            server.stop();
        }
    }

    @Test
    void testPortConfigurationLogic() throws IOException {
        // Find an available port
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // Test the configuration logic similar to HttpSteps.createWireMockConfiguration
        System.setProperty("tzatziki.http.port", String.valueOf(port));

        WireMockConfiguration config = WireMockConfiguration.wireMockConfig();
        String portProperty = System.getProperty("tzatziki.http.port");
        if (portProperty != null) {
            int configuredPort = Integer.parseInt(portProperty);
            config.port(configuredPort);
        } else {
            config.dynamicPort();
        }

        WireMockServer server = new WireMockServer(config);
        try {
            server.start();
            assertThat(server.port()).isEqualTo(port);
        } finally {
            server.stop();
        }
    }

    @Test
    void testInvalidPortConfigurationLogic() {
        System.setProperty("tzatziki.http.port", "invalid");

        // Test that invalid port throws appropriate exception
        assertThatThrownBy(() -> {
            String portProperty = System.getProperty("tzatziki.http.port");
            if (portProperty != null) {
                Integer.parseInt(portProperty);
            }
        }).isInstanceOf(NumberFormatException.class);
    }
}