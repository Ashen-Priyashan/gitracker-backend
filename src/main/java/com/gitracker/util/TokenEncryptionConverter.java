package com.gitracker.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
public class TokenEncryptionConverter implements AttributeConverter<String, String> {

    private static TokenEncryptionService encryptionService;

    public TokenEncryptionConverter() {
    }

    @Autowired
    public void setTokenEncryptionService(TokenEncryptionService service) {
        TokenEncryptionConverter.encryptionService = service;
    }

    public static void setStaticEncryptionService(TokenEncryptionService service) {
        TokenEncryptionConverter.encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        if (encryptionService == null) {
            throw new IllegalStateException("Token encryption service is not configured or unavailable");
        }
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        if (encryptionService == null) {
            throw new IllegalStateException("Token encryption service is not configured or unavailable");
        }
        return encryptionService.decrypt(dbData);
    }
}
