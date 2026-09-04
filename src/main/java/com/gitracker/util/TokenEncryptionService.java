package com.gitracker.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenEncryptionService {

    public static final String ENCRYPTION_PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public TokenEncryptionService(
            @Value("${security.encryption.secret-key:}") String configuredKey
    ) {
        if (configuredKey == null || configuredKey.trim().isBlank()) {
            throw new IllegalStateException(
                    "Token encryption secret key is not configured. " +
                    "Please set the ENCRYPTION_SECRET_KEY environment variable."
            );
        }

        this.secretKey = deriveSecretKey(configuredKey.trim());
        this.secureRandom = new SecureRandom();
    }

    private SecretKey deriveSecretKey(String keyInput) {
        try {
            byte[] keyBytes;
            // Support Base64-encoded 256-bit key or raw string hashed with SHA-256
            try {
                byte[] decoded = Base64.getDecoder().decode(keyInput);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = sha256(keyInput.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException e) {
                keyBytes = sha256(keyInput.getBytes(StandardCharsets.UTF_8));
            }

            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to derive AES-256 encryption key", e);
        }
    }

    private byte[] sha256(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(input);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        // If already encrypted, do not re-encrypt
        if (plaintext.startsWith(ENCRYPTION_PREFIX)) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token with AES-256-GCM", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }

        // Backward compatibility: If stored value is not prefixed, treat as legacy plaintext
        if (!storedValue.startsWith(ENCRYPTION_PREFIX)) {
            return storedValue;
        }

        try {
            String encodedPayload = storedValue.substring(ENCRYPTION_PREFIX.length());
            byte[] cipherBytes = Base64.getDecoder().decode(encodedPayload);

            if (cipherBytes.length < GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted payload is malformed or truncated");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherBytes);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token with AES-256-GCM", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }
}
