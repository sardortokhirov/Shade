package com.example.shade.service;

import com.example.shade.model.ExchangeRate;
import com.example.shade.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        BigDecimal uzsToRub = BigDecimal.ONE.divide(rubToUzs, 6, BigDecimal.ROUND_HALF_UP);

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .rubToUzs(rubToUzs)
                .uzsToRub(uzsToRub)
                .createdAt(LocalDateTime.now())
                .build();

        exchangeRateRepository.save(exchangeRate);
        log.info("Exchange rate updated: 1 RUB = {} UZS", rate);
    }
}
