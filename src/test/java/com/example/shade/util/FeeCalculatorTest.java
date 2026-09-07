package com.example.shade.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeeCalculatorTest {

    @Test
    void feeUsesFloorRounding() {
        assertEquals(50L, FeeCalculator.feeAmount(1000L, new BigDecimal("0.05")));
        assertEquals(0L, FeeCalculator.feeAmount(19L, new BigDecimal("0.05")));
        assertEquals(1L, FeeCalculator.feeAmount(20L, new BigDecimal("0.05")));
        assertEquals(999L, FeeCalculator.feeAmount(1000L, new BigDecimal("0.999")));
    }

    @Test
    void netIsAmountMinusFee() {
        assertEquals(950L, FeeCalculator.netAmount(1000L, new BigDecimal("0.05")));
        assertEquals(1000L, FeeCalculator.netAmount(1000L, BigDecimal.ZERO));
        assertEquals(1000L, FeeCalculator.netAmount(1000L, null));
    }

    @Test
    void rejectsNegativeAmountOrPercentage() {
        assertThrows(IllegalArgumentException.class,
                () -> FeeCalculator.feeAmount(-1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> FeeCalculator.feeAmount(100L, new BigDecimal("-0.01")));
    }
}
