package com.decathlon.tzatziki.utils;

import io.restassured.response.Response;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

import static io.restassured.RestAssured.given;

/**
 * Utility class for fetching OAuth2 access tokens using the client credentials flow.
 * <p>
 * This class performs HTTP POST requests to OAuth2 token endpoints to obtain
 * access tokens. It throws immediately on any failure.
 * </p>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OAuth2TokenFetcher {

    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    /**
     * Fetches an access token from the OAuth2 token endpoint using client credentials flow.
     *
     * @param clientId     the OAuth2 client ID
     * @param clientSecret the OAuth2 client secret
     * @param tokenUrl     the OAuth2 token endpoint URL
     * @return the access token
     * @throws AssertionError if the token request fails or the response is invalid
     */
    public static String fetchAccessToken(String clientId, String clientSecret, String tokenUrl) {
        log.debug("Fetching OAuth2 access token for clientId: {} from: {}", clientId, tokenUrl);

        // Resolve the token URL through HttpWiremockUtils to support mocked endpoints
        String resolvedTokenUrl = HttpWiremockUtils.target(tokenUrl);
        log.debug("Resolved token URL: {}", resolvedTokenUrl);

        // Encode client credentials in base64 for Basic Authorization
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        String authorizationHeader = "Basic " + encodedCredentials;

        try {
            Response response = given()
                    .contentType("application/x-www-form-urlencoded")
                    .header("Authorization", authorizationHeader)
                    .formParam("grant_type", GRANT_TYPE_CLIENT_CREDENTIALS)
                    .post(resolvedTokenUrl);

            int statusCode = response.getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new AssertionError(
                        "OAuth2 token request failed for clientId: " + clientId +
                                ". Status: " + statusCode +
                                ". Response: " + response.getBody().asString());
            }

            String accessToken = response.jsonPath().getString("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new AssertionError(
                        "OAuth2 token response does not contain 'access_token' for clientId: " + clientId +
                                ". Response: " + response.getBody().asString());
            }

            log.debug("Successfully fetched OAuth2 access token for clientId: {}", clientId);
            return accessToken;

        } catch (Error e) {
            if (e instanceof AssertionError) {
                throw e;
            }
            throw new AssertionError(
                    "Failed to fetch OAuth2 access token for clientId: " + clientId +
                            " from: " + tokenUrl + ". Error: " + e.getMessage(), e);
        }
    }
}
