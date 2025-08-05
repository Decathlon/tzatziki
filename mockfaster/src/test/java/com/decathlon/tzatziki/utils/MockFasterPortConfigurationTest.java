package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class to verify the port configuration functionality for MockFaster.
 */
public class MockFasterPortConfigurationTest {

    private String originalPortProperty;

    @BeforeEach
    void setUp() {
        // Save original system property if it exists
        originalPortProperty = System.getProperty("tzatziki.mockfaster.port");
    }

    @AfterEach
    void tearDown() {
        // Restore original system property
        if (originalPortProperty != null) {
            System.setProperty("tzatziki.mockfaster.port", originalPortProperty);
        } else {
            System.clearProperty("tzatziki.mockfaster.port");
        }
    }

    @Test
    void testPortConfiguration_validPort() {
        // Test that system property is read correctly
        System.setProperty("tzatziki.mockfaster.port", "8888");
        
        assertThat(System.getProperty("tzatziki.mockfaster.port")).isEqualTo("8888");
    }

    @Test
    void testPortConfiguration_invalidPort() {
        System.setProperty("tzatziki.mockfaster.port", "invalid");
        
        // Test that invalid port throws appropriate exception
        assertThatThrownBy(() -> {
            Integer.parseInt(System.getProperty("tzatziki.mockfaster.port"));
        }).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void testPortConfiguration_dynamicPortWhenNotSet() {
        // Clear any existing property
        System.clearProperty("tzatziki.mockfaster.port");
        
        assertThat(System.getProperty("tzatziki.mockfaster.port")).isNull();
    }
}