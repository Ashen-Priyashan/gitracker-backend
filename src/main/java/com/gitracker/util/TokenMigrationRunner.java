package com.gitracker.util;

import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.repository.GitHubAppConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TokenMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TokenMigrationRunner.class);

    private final GitHubAppConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;

    public TokenMigrationRunner(
            GitHubAppConnectionRepository connectionRepository,
            TokenEncryptionService encryptionService
    ) {
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            List<GitHubAppConnection> connections = connectionRepository.findAll();
            int migratedCount = 0;

            for (GitHubAppConnection connection : connections) {
                String token = connection.getAccessToken();
                if (token != null && !token.isBlank() && !encryptionService.isEncrypted(token)) {
                    connection.setAccessToken(encryptionService.encrypt(token));
                    connectionRepository.save(connection);
                    migratedCount++;
                }
            }

            if (migratedCount > 0) {
                logger.info("Migrated {} legacy plaintext GitHub OAuth token(s) to AES-256-GCM format.", migratedCount);
            }
        } catch (Exception e) {
            logger.warn("Token migration check skipped or deferred: {}", e.getMessage());
        }
    }
}
