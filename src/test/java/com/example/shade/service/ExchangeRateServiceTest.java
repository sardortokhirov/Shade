package com.example.shade.service;

import com.example.shade.model.ExchangeRate;
import com.example.shade.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExchangeRateServiceTest {

    @Test
    void telegramRateUpdateStoresRubPerThousandUzs() {
        ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
        ExchangeRateService service = new ExchangeRateService(repository);

        service.updateRate(160D);

        ArgumentCaptor<ExchangeRate> savedRate = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(repository).save(savedRate.capture());
        assertEquals(0, savedRate.getValue().getRubToUzs().compareTo(new BigDecimal("160")));
        assertEquals(0, savedRate.getValue().getUzsToRub().compareTo(new BigDecimal("6.250000")));
    }
}
