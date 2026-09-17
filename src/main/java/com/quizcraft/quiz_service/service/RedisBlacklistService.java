package com.quizcraft.quiz_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class RedisBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisBlacklistService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getRedisUrl() {
        String url = System.getProperty("REDIS_REST_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("REDIS_REST_URL");
        }
        return url != null ? url.trim() : null;
    }

    private String getRedisToken() {
        String token = System.getProperty("REDIS_REST_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            token = System.getenv("REDIS_REST_TOKEN");
        }
        return token != null ? token.trim() : null;
    }

    /**
     * Add token to blacklist in Redis with expiration time in seconds
     */
    public void blacklistToken(String token, long remainingSeconds) {
        String redisUrl = getRedisUrl();
        String redisToken = getRedisToken();

        if (redisUrl == null || redisToken == null) {
            log.warn("Upstash Redis REST not configured. Skip blacklisting token.");
            return;
        }

        long ttl = remainingSeconds > 0 ? remainingSeconds : 86400L;

        try {
            // Upstash REST endpoint: POST /set/{key}/{value}/EX/{seconds}
            String endpoint = redisUrl.replaceAll("/+$", "") + "/set/blacklist:" + token + "/true/EX/" + ttl;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + redisToken)
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Successfully blacklisted token in Redis (TTL: {}s)", ttl);
            } else {
                log.warn("Failed to blacklist token in Redis, status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error blacklisting token in Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if token is in blacklist
     */
    public boolean isBlacklisted(String token) {
        String redisUrl = getRedisUrl();
        String redisToken = getRedisToken();

        if (redisUrl == null || redisToken == null) {
            return false;
        }

        try {
            // Upstash REST endpoint: GET /get/{key}
            String endpoint = redisUrl.replaceAll("/+$", "") + "/get/blacklist:" + token;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + redisToken)
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode result = root.path("result");
                return !result.isNull() && !result.isMissingNode() && !"null".equalsIgnoreCase(result.asText());
            }
        } catch (Exception e) {
            log.warn("Error checking token blacklist in Redis: {}", e.getMessage());
        }

        return false;
    }
}
