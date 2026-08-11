package com.example.shade.service;

import com.example.shade.model.ExchangeRate;
import com.example.shade.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    public Double getLatestRate() {
        ExchangeRate rate = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("Valyuta kursi topilmadi"));
        return rate.getRubToUzs().doubleValue();
    }

    @Transactional
    public void updateRate(Double rate) {
        BigDecimal rubToUzs = BigDecimal.valueOf(rate);
        // All top-up code defines uzsToRub as RUB received for 1000 UZS.
        // Example: 1 RUB = 160 UZS means 1000 UZS = 6.25 RUB.
        BigDecimal uzsToRub = BigDecimal.valueOf(1000)
                .divide(rubToUzs, 6, RoundingMode.HALF_UP);

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .rubToUzs(rubToUzs)
                .uzsToRub(uzsToRub)
                .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                .build();

        exchangeRateRepository.save(exchangeRate);
        log.info("Exchange rate updated: 1 RUB = {} UZS", rate);
    }
}
