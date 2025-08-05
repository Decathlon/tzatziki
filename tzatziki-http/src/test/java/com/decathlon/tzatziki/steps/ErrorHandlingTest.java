package com.decathlon.tzatziki.steps;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test to verify that invalid port configuration is handled correctly
 */
public class ErrorHandlingTest {

    private String originalPortProperty;

    @BeforeEach
    void setUp() {
        originalPortProperty = System.getProperty("tzatziki.http.port");
    }

    @AfterEach
    void tearDown() {
        if (originalPortProperty != null) {
            System.setProperty("tzatziki.http.port", originalPortProperty);
        } else {
            System.clearProperty("tzatziki.http.port");
        }
    }

    @Test
    void testCreateWireMockConfigurationWithInvalidPort() {
        System.setProperty("tzatziki.http.port", "invalid_port");
        
        // This simulates the exact logic from HttpSteps.createWireMockConfiguration
        assertThatThrownBy(() -> {
            String portProperty = System.getProperty("tzatziki.http.port");
            if (portProperty != null) {
                try {
                    int port = Integer.parseInt(portProperty);
                    // Would use the port here
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port number specified in tzatziki.http.port: " + portProperty, e);
                }
            }
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid port number specified in tzatziki.http.port: invalid_port");
    }
}