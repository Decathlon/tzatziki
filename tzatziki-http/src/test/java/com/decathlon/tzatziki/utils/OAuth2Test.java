package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.decathlon.tzatziki.utils.HttpUtils.mockSimpleRequest;
import static com.decathlon.tzatziki.utils.Method.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2Test {
    
    @AfterEach
    void cleanupOAuth2() {
        OAuth2ClientCredentialsStore.reset();
    }

    @Test
    @DisplayName("should throw when fetching token for unregistered client")
    void throwsForUnregisteredClient() {
        assertThatThrownBy(() -> OAuth2ClientCredentialsStore.getAccessToken("unknown-client"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No OAuth2 access token found for clientId: unknown-client");
    }

    @Test
    @DisplayName("should correctly identify registered clients")
    void identifiesRegisteredClients() {
        // Register URL as mocked and get the mocked path
        String tokenUrl = "http://auth-server/oauth/token";
        String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
        mockSimpleRequest(mockedPath, POST, "200",
                "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\"}");

        // Register client
        OAuth2ClientCredentialsStore.registerClient("registered-client", "secret", tokenUrl);

        assertThat(OAuth2ClientCredentialsStore.hasClient("registered-client")).isTrue();
        assertThat(OAuth2ClientCredentialsStore.hasClient("unregistered-client")).isFalse();
    }

    @Test
    @DisplayName("should reset clears all cached tokens and configurations")
    void resetClearsAllData() {
        // Register URL as mocked and get the mocked path
        String tokenUrl = "http://auth-server/oauth/token";
        String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
        mockSimpleRequest(mockedPath, POST, "200",
                "{\"access_token\":\"token-to-clear\",\"token_type\":\"Bearer\"}");

        // Register client
        OAuth2ClientCredentialsStore.registerClient("client-to-reset", "secret", tokenUrl);
        assertThat(OAuth2ClientCredentialsStore.hasClient("client-to-reset")).isTrue();

        // Reset
        OAuth2ClientCredentialsStore.reset();

        // Verify client is no longer registered
        assertThat(OAuth2ClientCredentialsStore.hasClient("client-to-reset")).isFalse();
    }

    @Test
    @DisplayName("should throw when token endpoint returns error status")
    void throwsOnTokenEndpointError() {
        // Register URL as mocked and get the mocked path
        String tokenUrl = "http://auth-server-error/oauth/token";
        String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
        mockSimpleRequest(mockedPath, POST, "401",
                "{\"error\":\"invalid_client\",\"error_description\":\"Client authentication failed\"}");

        assertThatThrownBy(() -> OAuth2ClientCredentialsStore.registerClient(
                "invalid-client", "wrong-secret", tokenUrl))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("OAuth2 token request failed");
    }

    @Test
    @DisplayName("should throw when token response does not contain access_token")
    void throwsOnMissingAccessToken() {
        // Register URL as mocked and get the mocked path
        String tokenUrl = "http://auth-server-missing/oauth/token";
        String mockedPath = HttpWiremockUtils.mocked(tokenUrl);
        mockSimpleRequest(mockedPath, POST, "200",
                "{\"token_type\":\"Bearer\",\"expires_in\":3600}");

        assertThatThrownBy(() -> OAuth2ClientCredentialsStore.registerClient(
                "test-client", "test-secret", tokenUrl))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not contain 'access_token'");
    }
}
