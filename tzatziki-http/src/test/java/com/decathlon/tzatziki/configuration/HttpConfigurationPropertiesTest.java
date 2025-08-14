package com.decathlon.tzatziki.configuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.core.Options.DYNAMIC_PORT;

class HttpConfigurationPropertiesTest {

    @AfterEach
    void tearDown() {
        // Clean up system properties after each test
        System.clearProperty(HttpConfigurationProperties.HTTP_PORT);
        System.clearProperty(HttpConfigurationProperties.HTTP_MAX_CONCURRENT_REQUESTS);
    }

    @Test
    void getPortProperty_returnsDefaultDynamicPort_whenNoPropertySet() {
        int port = HttpConfigurationProperties.getPortProperty();
        Assertions.assertThat(port).isEqualTo(DYNAMIC_PORT);
    }

    @Test
    void getPortProperty_returnsConfiguredPort_whenValidPortSet() {
        System.setProperty(HttpConfigurationProperties.HTTP_PORT, "8080");

        int port = HttpConfigurationProperties.getPortProperty();

        Assertions.assertThat(port).isEqualTo(8080);
    }

    @Test
    void getPortProperty_throwsIllegalArgumentException_whenInvalidPortSet() {
        System.setProperty(HttpConfigurationProperties.HTTP_PORT, "invalid");

        Assertions.assertThatThrownBy(() -> HttpConfigurationProperties.getPortProperty())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid port number specified")
                .hasMessageContaining("tzatziki.http.port")
                .hasMessageContaining("invalid");
    }

    @Test
    void getMaxConcurrentRequestsProperty_returnsZero_whenNoPropertySet() {
        int maxConcurrentRequests = HttpConfigurationProperties.getMaxConcurrentRequestsProperty();
        Assertions.assertThat(maxConcurrentRequests).isEqualTo(0);
    }

    @Test
    void getMaxConcurrentRequestsProperty_returnsConfiguredValue_whenValidValueSet() {
        System.setProperty(HttpConfigurationProperties.HTTP_MAX_CONCURRENT_REQUESTS, "10");

        int maxConcurrentRequests = HttpConfigurationProperties.getMaxConcurrentRequestsProperty();

        Assertions.assertThat(maxConcurrentRequests).isEqualTo(10);
    }

    @Test
    void getMaxConcurrentRequestsProperty_returnsOne_whenSetToOne() {
        System.setProperty(HttpConfigurationProperties.HTTP_MAX_CONCURRENT_REQUESTS, "1");

        int maxConcurrentRequests = HttpConfigurationProperties.getMaxConcurrentRequestsProperty();

        Assertions.assertThat(maxConcurrentRequests).isEqualTo(1);
    }

    @Test
    void getMaxConcurrentRequestsProperty_throwsIllegalArgumentException_whenInvalidValueSet() {
        System.setProperty(HttpConfigurationProperties.HTTP_MAX_CONCURRENT_REQUESTS, "invalid");

        Assertions.assertThatThrownBy(() -> HttpConfigurationProperties.getMaxConcurrentRequestsProperty())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid maxConcurrentRequests specified")
                .hasMessageContaining("tzatziki.http.max-concurrent-requests")
                .hasMessageContaining("invalid");
    }
}
