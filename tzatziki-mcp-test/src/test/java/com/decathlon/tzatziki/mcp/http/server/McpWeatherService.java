package com.decathlon.tzatziki.mcp.http.server;

import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Service
public class McpWeatherService {

    @Value("${weather.api.base-url}")
    private String baseUrl;

    public record WeatherResponse(Current current) {
        public record Current(LocalDateTime time, int interval, double temperature_2m) {
        }
    }

    @McpTool(description = "Get the temperature (in celsius) for a specific location", generateOutputSchema = true)
    public WeatherResponse getTemperature(
            @McpToolParam(description = "The location latitude") double latitude,
            @McpToolParam(description = "The location longitude") double longitude) {

        return RestClient.create()
                .get()
                .uri(baseUrl + "/v1/forecast?latitude={latitude}&longitude={longitude}&current=temperature_2m",
                        latitude, longitude)
                .retrieve()
                .body(WeatherResponse.class);
    }

    @McpResource(uri = "weather://data/cities", name = "Cities Database",
            description = "Database of supported cities", mimeType = "application/json")
    public String cities() {
        return """
                [
                  { "name": "Paris", "country": "FR", "lat": 48.8566, "lon": 2.3522 },
                  { "name": "London", "country": "UK", "lat": 51.5074, "lon": -0.1278 },
                  { "name": "New York", "country": "US", "lat": 40.7128, "lon": -74.0060 },
                  { "name": "Tokyo", "country": "JP", "lat": 35.6762, "lon": 139.6503 },
                  { "name": "Sydney", "country": "AU", "lat": -33.8688, "lon": 151.2093 }
                ]
                """;
    }


    @McpPrompt(name = "temperature-alert", description = "Create a temperature alert message")
    public String temperatureAlert(
            @McpToolParam(description = "Threshold temperature") int threshold,
            @McpToolParam(description = "Alert location") String location) {
        return "Create a temperature alert for " + location + " when temperature exceeds " + threshold + " degrees Celsius.";
    }
}
