package com.example.shade.service;

import com.example.shade.model.OsonConfig;
import com.example.shade.repository.OsonConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OsonServiceTest {

    @Test
    void loginDoesNotReuseTokenAfterPrimaryConfigurationChanges() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OsonConfigRepository repository = mock(OsonConfigRepository.class);
        OsonConfig first = config("https://first.example", "+998000000001");
        OsonConfig second = config("https://second.example", "+998000000002");
        when(repository.findByPrimaryConfigTrue())
                .thenReturn(Optional.of(first), Optional.of(second));
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("errno", "0", "token", "token-1"), HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(Map.of("errno", "0", "token", "token-2"), HttpStatus.OK));

        OsonService service = new OsonService(restTemplate, repository);
        ReflectionTestUtils.invokeMethod(service, "login");
        ReflectionTestUtils.invokeMethod(service, "login");

        verify(repository, times(2)).findByPrimaryConfigTrue();
        verify(restTemplate).exchange(
                eq("https://first.example/api/user/login"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class));
        verify(restTemplate).exchange(
                eq("https://second.example/api/user/login"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class));
    }

    private OsonConfig config(String apiUrl, String phone) {
        return OsonConfig.builder()
                .apiUrl(apiUrl)
                .phone(phone)
                .password("password")
                .deviceId("device")
                .deviceName("device-name")
                .primaryConfig(true)
                .build();
    }
}
