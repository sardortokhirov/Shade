package com.example.shade.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Custom serializer for BigDecimal that always outputs plain decimal notation
 * without scientific notation (e.g., 0.00000001 instead of 1E-8)
 */
public class BigDecimalPlainSerializer extends JsonSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // Use toPlainString() to avoid scientific notation and write as raw JSON number
            gen.writeRawValue(value.toPlainString());
        }
    }
}
