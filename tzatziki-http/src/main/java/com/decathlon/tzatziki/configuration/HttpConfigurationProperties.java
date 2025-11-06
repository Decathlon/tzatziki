package com.decathlon.tzatziki.configuration;

import com.github.tomakehurst.wiremock.core.Options;

public class HttpConfigurationProperties {

    private HttpConfigurationProperties() {
    }

    public static final String HTTP_PORT = "tzatziki.http.port";
    public static final String HTTP_MAX_CONCURRENT_REQUESTS = "tzatziki.http.max-concurrent-requests";

    public static int getPortProperty() {
        String portProperty = System.getProperty(HTTP_PORT);
        int port = Options.DYNAMIC_PORT;
        if (portProperty != null) {
            try {
                port = Integer.parseInt(portProperty);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number specified in " + HTTP_PORT + ": " + portProperty, e);
            }
        }
        return port;
    }

    public static int getMaxConcurrentRequestsProperty() {
        String maxConcurrentRequestsProperty = System.getProperty(HTTP_MAX_CONCURRENT_REQUESTS);
        int maxConcurrentRequests = 0; // 0 means no limit
        if (maxConcurrentRequestsProperty != null) {
            try {
                maxConcurrentRequests = Integer.parseInt(maxConcurrentRequestsProperty);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid maxConcurrentRequests specified in " + HTTP_MAX_CONCURRENT_REQUESTS + ": " + maxConcurrentRequestsProperty, e);
            }
        }
        return maxConcurrentRequests;
    }
}
