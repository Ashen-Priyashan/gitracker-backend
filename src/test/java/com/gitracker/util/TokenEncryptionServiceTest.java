package com.gitracker.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new TokenEncryptionService("test_secret_key_32_characters_ok!");
    }

    @Test
    void shouldEncryptAndDecryptTokenSuccessfully() {
        String originalToken = "ghu_AbCdEf1234567890SecretOAuthToken";

        String encrypted = encryptionService.encrypt(originalToken);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals(originalToken, encrypted);
        assertFalse(encrypted.contains(originalToken));

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalToken, decrypted);
    }

    @Test
    void shouldProduceDifferentCiphertextsForSamePlaintextDueToRandomIv() {
        String token = "gho_StaticTokenSample123";

        String encrypted1 = encryptionService.encrypt(token);
        String encrypted2 = encryptionService.encrypt(token);

        assertNotEquals(encrypted1, encrypted2);
        assertEquals(token, encryptionService.decrypt(encrypted1));
        assertEquals(token, encryptionService.decrypt(encrypted2));
    }

    @Test
    void shouldReturnLegacyPlaintextTokenAsIsForBackwardCompatibility() {
        String legacyToken = "ghu_LegacyUnencryptedToken12345";

        String result = encryptionService.decrypt(legacyToken);

        assertEquals(legacyToken, result);
    }

    @Test
    void shouldHandleNullAndBlankSafely() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
        assertEquals("", encryptionService.encrypt(""));
        assertEquals("", encryptionService.decrypt(""));
    }

    @Test
    void shouldFailSafelyWhenKeyIsMissingOrBlank() {
        assertThrows(IllegalStateException.class, () -> new TokenEncryptionService(null));
        assertThrows(IllegalStateException.class, () -> new TokenEncryptionService("   "));
    }

    @Test
    void shouldFailWhenCiphertextIsTampered() {
        String token = "ghp_SecretToken";
        String encrypted = encryptionService.encrypt(token);

        // Tamper with the ciphertext body
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    void shouldWorkViaJpaConverter() {
        TokenEncryptionConverter converter = new TokenEncryptionConverter();
        converter.setTokenEncryptionService(encryptionService);

        String rawToken = "ghu_DatabaseColumnTestToken";
        String dbColumn = converter.convertToDatabaseColumn(rawToken);

        assertTrue(dbColumn.startsWith("enc:v1:"));
        String entityAttr = converter.convertToEntityAttribute(dbColumn);

        assertEquals(rawToken, entityAttr);
    }

    @Test
    void shouldFailClosedWhenConverterEncryptionServiceIsNull() {
        TokenEncryptionConverter converter = new TokenEncryptionConverter();
        converter.setTokenEncryptionService(null);

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                converter.convertToDatabaseColumn("ghu_sensitive_token"));
        assertTrue(ex1.getMessage().contains("Token encryption service is not configured or unavailable"));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                converter.convertToEntityAttribute("enc:v1:some_ciphertext"));
        assertTrue(ex2.getMessage().contains("Token encryption service is not configured or unavailable"));

        // Null and blank should still return safely without throwing
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToEntityAttribute(""));
    }
}
