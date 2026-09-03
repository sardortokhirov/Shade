package com.example.shade.service;

import com.example.shade.model.LotteryConfiguration;
import com.example.shade.repository.LotteryConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryConfigServiceBuyOfferMinTest {

    private LotteryConfigurationRepository repository;
    private LotteryConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(LotteryConfigurationRepository.class);
        service = new LotteryConfigService(repository);
    }

    @Test
    void buyOfferMinIsTenPercentBelowSellMin() {
        LotteryConfiguration config = new LotteryConfiguration();
        config.setP2pMinPricePerTicket(10_000L);
        config.setP2pFeePercentage(BigDecimal.ZERO);
        when(repository.findLatest()).thenReturn(Optional.of(config));

        assertEquals(9_000L, service.getP2pBuyOfferMinPricePerTicket());
        assertEquals(10_000L, service.getP2pMinPricePerTicket());
    }

    @Test
    void buyOfferMinFloorsAndNeverBelowOne() {
        LotteryConfiguration config = new LotteryConfiguration();
        config.setP2pMinPricePerTicket(1L);
        when(repository.findLatest()).thenReturn(Optional.of(config));
        assertEquals(1L, service.getP2pBuyOfferMinPricePerTicket());

        config.setP2pMinPricePerTicket(15L);
        assertEquals(14L, service.getP2pBuyOfferMinPricePerTicket());
    }
}
