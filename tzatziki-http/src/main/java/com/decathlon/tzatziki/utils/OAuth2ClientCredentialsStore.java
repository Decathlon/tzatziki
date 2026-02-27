package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for OAuth2 client credentials configurations and cached access tokens.
 * <p>
 * This class manages OAuth2 client credentials (clientId, clientSecret, tokenUrl) and
 * caches the fetched access tokens per clientId. Tokens are fetched once when the client
 * is registered and cached for the duration of the test scenario.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OAuth2ClientCredentialsStore {

    private static final Map<String, OAuth2ClientConfig> clientConfigs = new ConcurrentHashMap<>();
    private static final Map<String, String> accessTokens = new ConcurrentHashMap<>();

    /**
     * Registers a new OAuth2 client and immediately fetches the access token.
     * If the client is already registered with the same configuration, the cached token is reused.
     *
     * @param clientId     the OAuth2 client ID
     * @param clientSecret the OAuth2 client secret
     * @param tokenUrl     the OAuth2 token endpoint URL
     * @throws AssertionError if token fetch fails
     */
    public static void registerClient(String clientId, String clientSecret, String tokenUrl) {
        OAuth2ClientConfig newConfig = new OAuth2ClientConfig(clientId, clientSecret, tokenUrl);
        OAuth2ClientConfig existingConfig = clientConfigs.get(clientId);

        // Skip if client is already registered with the same configuration
        if (newConfig.equals(existingConfig) && accessTokens.containsKey(clientId)) {
            return;
        }

        clientConfigs.put(clientId, newConfig);

        // Fetch token immediately and cache it
        String accessToken = OAuth2TokenFetcher.fetchAccessToken(clientId, clientSecret, tokenUrl);
        accessTokens.put(clientId, accessToken);
    }

    /**
     * Gets the cached access token for the given clientId.
     *
     * @param clientId the OAuth2 client ID
     * @return the cached access token
     * @throws AssertionError if no token is found for the clientId
     */
    public static String getAccessToken(String clientId) {
        String token = accessTokens.get(clientId);
        if (token == null) {
            throw new AssertionError("No OAuth2 access token found for clientId: " + clientId +
                    ". Please setup authentication first using: Setup authentication for clientId \"" +
                    clientId + "\" with clientSecret \"...\" and token url \"...\"");
        }
        return token;
    }

    /**
     * Checks if a client is registered.
     *
     * @param clientId the OAuth2 client ID
     * @return true if the client is registered, false otherwise
     */
    public static boolean hasClient(String clientId) {
        return clientConfigs.containsKey(clientId);
    }

    /**
     * Resets the store, clearing all cached tokens and configurations.
     * Should be called between test scenarios.
     */
    public static void reset() {
        clientConfigs.clear();
        accessTokens.clear();
    }

    /**
     * Internal configuration holder for OAuth2 client credentials.
     */
    public record OAuth2ClientConfig(String clientId, String clientSecret, String tokenUrl) {
    }
}
