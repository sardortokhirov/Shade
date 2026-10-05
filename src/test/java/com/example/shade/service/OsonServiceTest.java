package com.example.shade.service;

import com.example.shade.model.OsonConfig;
import com.example.shade.repository.OsonConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsonServiceTest {
    private static final String BASE_URL = "https://oson.test";
    private static final DateTimeFormatter OSON_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    @Test
    void autoConfirmationUsesFreshLoginForCardHistoryAndMatchesPayment() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OsonConfigRepository configRepository = mock(OsonConfigRepository.class);
        when(configRepository.findByPrimaryConfigTrue()).thenReturn(Optional.of(config()));

        server.expect(once(), requestTo(BASE_URL + "/api/user/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errno\":\"0\",\"token\":\"card-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/api/user/card_v2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"errno\":\"0\",\"array\":[{\"number\":\"8600123412345678\",\"id\":42}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/api/user/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errno\":\"0\",\"token\":\"history-token\"}", MediaType.APPLICATION_JSON));

        String timestamp = OffsetDateTime.now(ZoneId.of("GMT+5")).minusMinutes(1).format(OSON_TIMESTAMP);
        server.expect(once(), requestTo(BASE_URL
                        + "/api/user/card_history?card_id=42&count=20&manufacturer=1&offset=0&version=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"errno\":\"0\",\"array\":[{\"amount\":1469700,\"ts\":\"" + timestamp
                                + "\",\"status\":1,\"id\":123,\"refnum\":456}]}",
                        MediaType.APPLICATION_JSON));

        OsonService service = new OsonService(restTemplate, configRepository);
        Map<String, Object> result = service.verifyPaymentByAmountAndCard(
                100L, "MELBET", "player", 14_697L, null, "8600123412345678", 14_697L);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals("123", result.get("transactionId"));
        assertEquals(456, result.get("billId"));
        server.verify();
    }

    private OsonConfig config() {
        return OsonConfig.builder()
                .apiUrl(BASE_URL)
                .phone("998900000000")
                .password("password")
                .deviceId("device")
                .deviceName("test")
                .primaryConfig(true)
                .build();
    }
}
