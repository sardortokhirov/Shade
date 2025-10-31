package com.example.shade.service;

import com.example.shade.dto.BalanceLimit;
import com.example.shade.model.*;
import com.example.shade.repository.ExchangeRateRepository;
import com.example.shade.repository.PlatformRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@AllArgsConstructor
public class MostbetService {

    private static final String BASE_URL = "https://apimb.com";
    // FIX: Define the full API path prefix required for the signature.
    private static final String API_PATH_PREFIX = "/mbc/gateway/v1/api/cashpoint";
    private static final String FULL_BASE_URL = BASE_URL + API_PATH_PREFIX; // Used for RestTemplate calls

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern(DATE_FORMAT);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // Credentials and constants from your code
//    private final String apiKey = "api-key:36005f90-644a-4a56-8002-7ec52a26a257";
//    private final String apiKey = "api-key:8d014548-0b05-47e1-8f03-dd7018f656f8";
//    private final String secret = "c0514a94-a420-4080-bed8-b7336808ae81";
//    private final String secret = "8c23c4aa-c228-448b-a86d-c04077c39603";
    private final String project = "MBC";

    private final PlatformRepository  platformRepository;;
    private final ExchangeRateRepository exchangeRateRepository;;
    private String now() {
        return LocalDateTime.now(ZoneOffset.UTC).format(FMT);
    }

    private String sign(String apiKey,String secret,String path, String body, String timestamp) throws Exception {
        String data = apiKey + path + (body != null ? body : "") + timestamp;
        Mac mac = Mac.getInstance("HmacSHA3-256");
        // FIX: Specify UTF-8 encoding for consistency.
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA3-256"));
        return Hex.encodeHexString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpHeaders headers(String apiKey,String secret,String path, String body) throws Exception {
        String ts = now();
        HttpHeaders h = new HttpHeaders();
        h.set("X-Api-Key", apiKey);
        h.set("X-Timestamp", ts);
        h.set("X-Signature", sign(apiKey, secret,path, body, ts));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    public BalanceResponse getBalance(String apiKey,String secret,String cashpointId) throws Exception {
        // FIX: Construct the full path for the signature.
        String path = API_PATH_PREFIX + "/" + cashpointId + "/balance";
        // Note: The body for GET requests is an empty string, which is correct.
        HttpEntity<?> req = new HttpEntity<>(headers(apiKey,secret,path, ""));
        String url = FULL_BASE_URL + "/" + cashpointId + "/balance";
        ResponseEntity<BalanceResponse> resp = restTemplate.exchange(url, HttpMethod.GET, req, BalanceResponse.class);
        return resp.getBody();
    }

    public TransactionResponse deposit(String apiKey,String secret,String cashpointId, int brandId, String playerId, double amount, String currency) throws Exception {
        // FIX: Construct the full path for the signature.
        String path = API_PATH_PREFIX + "/" + cashpointId + "/player/deposit";
        DepositRequest body = new DepositRequest(1, playerId, amount, currency);
        // Note: By default, ObjectMapper produces a compact JSON string without extra spaces, which is correct per the documentation.
        String json = mapper.writeValueAsString(body);
        HttpHeaders h = headers( apiKey, secret,path, json);
        h.set("X-Project", project);
        HttpEntity<String> req = new HttpEntity<>(json, h);
        String url = FULL_BASE_URL + "/" + cashpointId + "/player/deposit";
        ResponseEntity<TransactionResponse> resp = restTemplate.exchange(url, HttpMethod.POST, req, TransactionResponse.class);
        return resp.getBody();
    }



    public TransactionResponse confirmCashout(String apiKey,String secret,String cashpointId, String code, long transactionId) throws Exception {
        // FIX: Construct the full path for the signature.
        String path = API_PATH_PREFIX + "/" + cashpointId + "/player/cashout/confirmation";
        CashoutConfirm body = new CashoutConfirm(code, transactionId);
        String json = mapper.writeValueAsString(body);
        HttpHeaders h = headers(apiKey,secret,path, json);
        h.set("X-Project", project);
        HttpEntity<String> req = new HttpEntity<>(json, h);
        String url = FULL_BASE_URL + "/" + cashpointId + "/player/cashout/confirmation";
        ResponseEntity<TransactionResponse> resp = restTemplate.exchange(url, HttpMethod.POST, req, TransactionResponse.class);
        return resp.getBody();
    }



    public BalanceLimit transferToPlatform(HizmatRequest request, AdminCard adminCard) throws Exception {
        String platformName = request.getPlatform();
        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));
        String apiKey = platform.getApiKey();
        String secret = platform.getSecret();
        String cashpointId = platform.getWorkplaceId();
        String userId = request.getPlatformUserId();
        long amount = request.getUniqueAmount();

        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        if (request.getCurrency().equals(Currency.RUB)) {
            amount = BigDecimal.valueOf(request.getUniqueAmount())
                    .multiply(latest.getUzsToRub())
                    .longValue() / 1000;
        }

        deposit(  apiKey, secret,cashpointId, 1, userId, 1000, platform.getCurrency().toString());
        BalanceResponse balance = getBalance( apiKey, secret,cashpointId);

        return new BalanceLimit(null,new BigDecimal(balance.balance));
    }


    public record BalanceResponse(double balance, String currency) {}
    public record DepositRequest(int brandId, String playerId, double amount, String currency) {}
    public record TransactionResponse(long transactionId, String status) {}
    public record CashoutConfirm(String code, long transactionId) {}
    public record TransactionItem(long transactionId, String type, String status, String subject, int brandId, String playerId, String date, double amount, String currency) {}
    public record TransactionListResponse(List<TransactionItem> items) {}
}