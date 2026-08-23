package io.github.mortogo321.recon.core.entity;

import java.util.Currency;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Currency} as its ISO code. Auto-applied so no entity has to opt in. */
@Converter(autoApply = true)
public class CurrencyConverter implements AttributeConverter<Currency, String> {

    @Override
    public String convertToDatabaseColumn(Currency attribute) {
        return attribute == null ? null : attribute.getCurrencyCode();
    }

    @Override
    public Currency convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : Currency.getInstance(dbData.trim());
    }
}
