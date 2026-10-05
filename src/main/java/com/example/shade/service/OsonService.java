package com.example.shade.service;

import com.example.shade.model.OsonConfig;
import com.example.shade.repository.OsonConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OsonService {
    private static final Logger logger = LoggerFactory.getLogger(OsonService.class);
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private final RestTemplate restTemplate;
    private final OsonConfigRepository osonConfigRepository;
    private static final DateTimeFormatter OSON_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
    private volatile String authToken;

    private OsonConfig getConfig() {
        return osonConfigRepository.findByPrimaryConfigTrue()
                .orElseThrow(() -> new IllegalStateException("Oson configuration not found"));
    }

    private synchronized String login() {
        if (authToken != null && !authToken.isBlank()) {
            return authToken;
        }
        OsonConfig config = getConfig();
        String url = config.getApiUrl() + "/api/user/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
        headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
        headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
        headers.set("Connection", "keep-alive");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("app_version", "11.4.9");
        body.add("dev_id", config.getDeviceId());
        body.add("device_name", config.getDeviceName());
        body.add("lang", "1");
        body.add("password", config.getPassword());
        body.add("phone", config.getPhone());
        body.add("platform", "ios");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null && "0".equals(String.valueOf(responseBody.get("errno")))) {
                authToken = (String) responseBody.get("token");
                logger.info("Oson login successful");
                return authToken;
            } else {
                logger.error("Oson login failed: {}", responseBody != null ? responseBody.get("errstr") : "No response body");
                throw new RuntimeException("Oson login failed: " + (responseBody != null ? responseBody.get("errstr") : "Unknown error"));
            }
        } catch (HttpClientErrorException e) {
            logger.error("Oson login HTTP error: {}", e.getMessage());
            throw new RuntimeException("Oson login failed: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            logger.error("Unexpected error during Oson login: {}", e.getMessage());
            throw new RuntimeException("Oson login failed: " + e.getMessage());
        }
    }

    private String getAuthToken() {
        String token = authToken;
        return token != null && !token.isBlank() ? token : login();
    }

    private Long getCardIdByNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            throw new IllegalArgumentException("Admin card number must contain at least four digits");
        }
        OsonConfig config = getConfig();
        String url = config.getApiUrl() + "/api/user/card_v2";

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("token", getAuthToken());
                headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
                headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
                headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
                headers.set("Connection", "keep-alive");

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                Map<String, Object> responseBody = response.getBody();
                if (response.getStatusCode().is2xxSuccessful() && responseBody != null
                        && "0".equals(String.valueOf(responseBody.get("errno")))) {
                    List<Map<String, Object>> cards = (List<Map<String, Object>>) responseBody.get("array");
                    if (cards == null) {
                        return null;
                    }
                    String lastFour = cardNumber.substring(cardNumber.length() - 4);
                    for (Map<String, Object> card : cards) {
                        String number = (String) card.get("number");
                        if (number != null && number.endsWith(lastFour)) {
                            return Long.valueOf(String.valueOf(card.get("id")));
                        }
                    }
                    return null;
                }

                authToken = null;
                logger.warn("Oson card lookup returned errno {}, attempt {}/{}",
                        responseBody != null ? responseBody.get("errno") : null,
                        attempt, MAX_RETRY_ATTEMPTS);
            } catch (HttpClientErrorException.Unauthorized e) {
                authToken = null;
                logger.warn("Oson card lookup unauthorized, attempt {}/{}", attempt, MAX_RETRY_ATTEMPTS);
            } catch (ResourceAccessException e) {
                logger.error("Oson card lookup timed out or could not connect: {}", e.getMessage());
                throw new RuntimeException("Oson API timeout or connection error", e);
            } catch (RestClientException e) {
                logger.error("Oson card lookup failed: {}", e.getMessage());
                throw new RuntimeException("Oson API request failed", e);
            } catch (Exception e) {
                logger.error("Unexpected Oson card lookup error: {}", e.getMessage(), e);
                throw new RuntimeException("Unexpected Oson API error", e);
            }
        }

        throw new RuntimeException("Oson card lookup failed after " + MAX_RETRY_ATTEMPTS + " attempts");
    }

    public Map<String, Object> verifyPaymentByAmountAndCard(Long chatId, String platform, String platformUserId, long amount, String userCardNumber, String adminCardId, long uniqueAmount) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("error", "Payment verification failed");
        long adjustedAmount = uniqueAmount * 100; // Adjust for Oson API (assuming amount in tiyin)

        Long cardId;
        try {
            cardId = getCardIdByNumber(adminCardId);
        } catch (RuntimeException e) {
            logger.error("Failed to get Oson card ID for chatId {}: {}", chatId, e.getMessage());
            response.put("error", e.getMessage());
            return response;
        }
        if (cardId == null) {
            response.put("error", "Admin card not found");
            return response;
        }

        OsonConfig config = getConfig();
        String url = String.format("%s/api/user/card_history?card_id=%d&count=20&manufacturer=1&offset=0&version=2", config.getApiUrl(), cardId);

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("token", getAuthToken());
                headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
                headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
                headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
                headers.set("Connection", "keep-alive");

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                Map<String, Object> responseBody = apiResponse.getBody();
                if (apiResponse.getStatusCode().is2xxSuccessful() && responseBody != null
                        && "0".equals(String.valueOf(responseBody.get("errno")))) {
                    List<Map<String, Object>> transactions = (List<Map<String, Object>>) responseBody.get("array");
                    OffsetDateTime now = OffsetDateTime.now();

                    if (transactions != null) {
                        for (Map<String, Object> transaction : transactions) {
                            long txAmount = Long.parseLong(String.valueOf(transaction.get("amount")));
                            String ts = (String) transaction.get("ts");
                            int status = Integer.parseInt(String.valueOf(transaction.get("status")));

                            if (txAmount == adjustedAmount && status == 1) {
                                try {
                                    OffsetDateTime txTime = OffsetDateTime.parse(ts, OSON_TIMESTAMP_FORMATTER);
                                    if (txTime.isAfter(now.minusMinutes(15))) {
                                        response.put("status", "SUCCESS");
                                        response.put("transactionId", String.valueOf(transaction.get("id")));
                                        response.put("billId", transaction.get("refnum"));
                                        response.put("payUrl", "");
                                        return response;
                                    }
                                } catch (DateTimeParseException e) {
                                    logger.error("Failed to parse timestamp '{}': {}", ts, e.getMessage());
                                    response.put("error", "Invalid timestamp format in transaction: " + ts);
                                    return response;
                                }
                            }
                        }
                    }
                    response.put("error", "No matching payment found");
                    return response;
                }

                authToken = null;
                logger.warn("Oson payment verification returned errno {}, attempt {}/{}",
                        responseBody != null ? responseBody.get("errno") : null,
                        attempt, MAX_RETRY_ATTEMPTS);
            } catch (HttpClientErrorException.Unauthorized e) {
                authToken = null;
                logger.warn("Oson payment verification unauthorized, attempt {}/{}",
                        attempt, MAX_RETRY_ATTEMPTS);
            } catch (ResourceAccessException e) {
                logger.error("Oson payment verification timed out for chatId {}: {}", chatId, e.getMessage());
                response.put("error", "Oson API timeout or connection error");
                return response;
            } catch (RestClientException e) {
                logger.error("Oson payment verification request failed for chatId {}: {}", chatId, e.getMessage());
                response.put("error", "Oson API request failed");
                return response;
            } catch (Exception e) {
                logger.error("Error verifying Oson payment for chatId {}: {}", chatId, e.getMessage(), e);
                response.put("error", "Verification error: " + e.getMessage());
                return response;
            }
        }

        response.put("error", "Oson API failed after " + MAX_RETRY_ATTEMPTS + " attempts");
        return response;
    }
}
