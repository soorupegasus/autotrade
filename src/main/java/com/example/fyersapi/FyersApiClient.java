package com.example.fyersapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight wrapper around the FYERS REST API (v3).
 * <p>
 * Documentation: https://myapi.fyers.in/docsv3
 * <p>
 * This client intentionally covers only a small subset of the available endpoints –
 * enough to demonstrate authentication and a few common calls. You can extend it
 * as needed by referring to the official docs.
 */
public class FyersApiClient {
    private static final Logger log = LoggerFactory.getLogger(FyersApiClient.class);

    // Base URLs – switch between production / sandbox by editing here.
    private static final String BASE_URL = "https://api.fyers.in/api/v3";  // production
    private static final String LOGIN_URL = "https://api.fyers.in/api/v3/generate-authcode";

    private final String clientId;          // Also called app_id in the docs
    private final String secretKey;
    private final String redirectUri;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    private String accessToken;  // Bearer token used for subsequent requests
    private String refreshToken;

    public FyersApiClient(String clientId, String secretKey, String redirectUri) {
        this.clientId = Objects.requireNonNull(clientId);
        this.secretKey = Objects.requireNonNull(secretKey);
        this.redirectUri = Objects.requireNonNull(redirectUri);
        this.http = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /* =====================================================================================
     *  Authentication helpers
     * ===================================================================================== */

    /**
     * Builds the URL that the user must open in a browser to complete the login flow.
     * After successful login, FYERS will redirect to {@code redirectUri} with a
     * {@code ?auth_code=} query parameter. Use that value with {@link #generateAccessToken(String)}.
     */
    public String buildLoginUrl(String state) {
        try {
            return LOGIN_URL + "?client_id=" + url(clientId) +
                    "&redirect_uri=" + url(redirectUri) +
                    "&response_type=code&state=" + url(state);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build login URL", e);
        }
    }

    /**
     * Exchange the authorization code (obtained from browser redirect) for an access token.
     * The access token is stored internally for later requests.
     */
    public void generateAccessToken(String authCode) {
        String url = BASE_URL + "/validate-authcode";
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("appIdHash", secretKey);      // per docs: SHA-256 hash of appSecretKey but FYERS calls it appIdHash
        body.put("appId", clientId);
        body.put("code", authCode);
        body.put("redirect_uri", redirectUri);

        Map<String, Object> response = postJson(url, body);
        if (Boolean.TRUE.equals(response.get("s"))) {
            // Successful
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            this.accessToken = (String) data.get("access_token");
            this.refreshToken = (String) data.get("refresh_token");
            log.info("Successfully obtained access token. It expires in {} seconds", data.get("expires_in"));
        } else {
            throw new FyersApiException("Failed to generate access token: " + response);
        }
    }

    /**
     * Refresh access token using the longer-lived refresh token.
     */
    public void refreshAccessToken() {
        if (refreshToken == null) {
            throw new IllegalStateException("Refresh token not available. Call generateAccessToken first.");
        }
        String url = BASE_URL + "/validate-authcode";  // Same endpoint with different grant_type
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("appIdHash", secretKey);
        body.put("appId", clientId);
        body.put("refresh_token", refreshToken);

        Map<String, Object> response = postJson(url, body);
        if (Boolean.TRUE.equals(response.get("s"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            this.accessToken = (String) data.get("access_token");
            this.refreshToken = (String) data.get("refresh_token");
            log.info("Successfully refreshed access token.");
        } else {
            throw new FyersApiException("Failed to refresh token: " + response);
        }
    }

    /* =====================================================================================
     *  API endpoints – examples
     * ===================================================================================== */

    public Map<String, Object> getProfile() {
        return getJson(BASE_URL + "/profile");
    }

    /**
     * Fetch latest market quotes for the given comma-separated symbol strings, e.g. "NSE:SBIN-EQ,NSE:INFY-EQ".
     */
    public Map<String, Object> getQuotes(String symbolsCsv) {
        String url = BASE_URL + "/quotes?symbols=" + url(symbolsCsv);
        return getJson(url);
    }

    /**
     * Place a simple market order. For advanced parameters refer to docs.
     */
    public Map<String, Object> placeMarketOrder(String symbol, int qty, String side) {
        String url = BASE_URL + "/orders";
        Map<String, Object> body = new HashMap<>();
        body.put("symbol", symbol);
        body.put("qty", qty);
        body.put("type", 2);        // 2 => market order as per docs
        body.put("side", side);      // "BUY" or "SELL"
        body.put("productType", "INTRADAY");
        body.put("limitPrice", 0);
        body.put("stopPrice", 0);
        body.put("disclosedQty", 0);
        body.put("validity", "DAY");
        body.put("offlineOrder", "False");
        body.put("takeProfit", 0);
        body.put("stopLoss", 0);

        return postJson(url, body, true);
    }

    /* =====================================================================================
     *  Internal helpers
     * ===================================================================================== */

    private Map<String, Object> getJson(String url) {
        Request.Builder builder = new Request.Builder().url(url).get();
        addAuthHeader(builder);
        Request request = builder.build();
        return execute(request);
    }

    private Map<String, Object> postJson(String url, Object body) {
        return postJson(url, body, false);
    }

    private Map<String, Object> postJson(String url, Object body, boolean authenticated) {
        try {
            String json = mapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));
            Request.Builder builder = new Request.Builder().url(url).post(requestBody);
            if (authenticated) {
                addAuthHeader(builder);
            }
            Request request = builder.build();
            return execute(request);
        } catch (IOException e) {
            throw new FyersApiException("Failed to serialize request body", e);
        }
    }

    private void addAuthHeader(Request.Builder builder) {
        if (accessToken == null) {
            throw new IllegalStateException("Access token not available. Authenticate first.");
        }
        builder.addHeader("Authorization", "Bearer " + accessToken);
    }

    private Map<String, Object> execute(Request request) {
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new FyersApiException("HTTP error: " + response.code() + " " + response.message());
            }
            String body = Objects.requireNonNull(response.body()).string();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(body, new TypeReference<>() {
            });
            return map;
        } catch (IOException e) {
            throw new FyersApiException("HTTP request failed", e);
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}