package com.gitracker.service;

import com.gitracker.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final String ACCOUNT_KEY_PREFIX = "gitracker:auth:login:account:";
    private static final String IP_KEY_PREFIX = "gitracker:auth:login:ip:";

    // Atomic Lua script: increments counter and sets TTL strictly once (on initial creation or immortal key)
    // Non-refreshing TTL: subsequent increments do not call EXPIRE, preventing sliding/permanent lockout
    private static final String INCR_WITH_BOUNDED_TTL_LUA =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 or redis.call('TTL', KEYS[1]) == -1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int accountMaxAttempts;
    private final long accountWindowSeconds;
    private final int ipMaxAttempts;
    private final long ipWindowSeconds;
    private final String redisFailurePolicy;
    private final DefaultRedisScript<Long> incrScript;

    @Autowired
    public LoginRateLimiter(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Value("${security.login-rate-limit.enabled:true}") boolean enabled,
            @Value("${security.login-rate-limit.account.max-attempts:5}") int accountMaxAttempts,
            @Value("${security.login-rate-limit.account.window-seconds:300}") long accountWindowSeconds,
            @Value("${security.login-rate-limit.ip.max-attempts:20}") int ipMaxAttempts,
            @Value("${security.login-rate-limit.ip.window-seconds:300}") long ipWindowSeconds,
            @Value("${security.login-rate-limit.redis-failure-policy:FAIL_CLOSED}") String redisFailurePolicy
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.accountMaxAttempts = accountMaxAttempts;
        this.accountWindowSeconds = accountWindowSeconds;
        this.ipMaxAttempts = ipMaxAttempts;
        this.ipWindowSeconds = ipWindowSeconds;
        this.redisFailurePolicy = redisFailurePolicy;
        this.incrScript = new DefaultRedisScript<>(INCR_WITH_BOUNDED_TTL_LUA, Long.class);
    }

    /**
     * Pre-check rate limits before performing database lookup or expensive password verification.
     * Evaluates IP abuse limit first, followed by account-level throttling.
     *
     * @param normalizedEmail lowercase, trimmed email
     * @param clientIp resolved client IP
     * @throws RateLimitExceededException if throttled
     */
    public void checkRateLimit(String normalizedEmail, String clientIp) {
        if (!enabled || redisTemplate == null) {
            return;
        }

        try {
            // 1. Check IP-level abuse limit first
            String ipKey = buildIpKey(clientIp);
            Long ipCount = getCount(ipKey);
            if (ipCount != null && ipCount >= ipMaxAttempts) {
                Long ttl = redisTemplate.getExpire(ipKey, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : ipWindowSeconds;
                long resetTime = Instant.now().getEpochSecond() + retryAfter;
                log.warn("Login throttled for client IP [{}]: {} failed attempts", clientIp, ipCount);
                throw new RateLimitExceededException("Too many login attempts. Please try again later.", retryAfter, resetTime);
            }

            // 2. Check Account-level abuse limit second
            String accountKey = buildAccountKey(normalizedEmail);
            Long accountCount = getCount(accountKey);
            if (accountCount != null && accountCount >= accountMaxAttempts) {
                Long ttl = redisTemplate.getExpire(accountKey, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : accountWindowSeconds;
                long resetTime = Instant.now().getEpochSecond() + retryAfter;
                log.warn("Login throttled for account hash [{}]: {} failed attempts", sha256Hex(normalizedEmail), accountCount);
                throw new RateLimitExceededException("Too many login attempts. Please try again later.", retryAfter, resetTime);
            }
        } catch (RateLimitExceededException rle) {
            throw rle;
        } catch (Exception e) {
            handleRedisException("checkRateLimit", e);
        }
    }

    /**
     * Records a failed authentication attempt atomically in Redis for both account and client IP.
     * Implements bounded-memory protections: skips account-key creation if client IP is already throttled.
     *
     * @param normalizedEmail lowercase, trimmed email
     * @param clientIp resolved client IP
     */
    public void recordFailedAttempt(String normalizedEmail, String clientIp) {
        if (!enabled || redisTemplate == null) {
            return;
        }

        try {
            String ipKey = buildIpKey(clientIp);

            // Bounded-memory protection: if client IP is already throttled, do not allocate/update account keys
            Long ipCount = getCount(ipKey);
            if (ipCount != null && ipCount >= ipMaxAttempts) {
                return;
            }

            // Increment IP counter with bounded TTL
            redisTemplate.execute(incrScript, Collections.singletonList(ipKey), String.valueOf(ipWindowSeconds));

            // Increment Account counter with bounded TTL
            String accountKey = buildAccountKey(normalizedEmail);
            redisTemplate.execute(incrScript, Collections.singletonList(accountKey), String.valueOf(accountWindowSeconds));

        } catch (Exception e) {
            handleRedisException("recordFailedAttempt", e);
        }
    }

    /**
     * Clears account-specific failed attempt state upon successful credential verification.
     * Preserves broader client IP failure state to prevent attackers from using one valid account to reset IP abuse.
     *
     * @param normalizedEmail lowercase, trimmed email
     */
    public void recordSuccessfulLogin(String normalizedEmail) {
        if (!enabled || redisTemplate == null) {
            return;
        }

        try {
            String accountKey = buildAccountKey(normalizedEmail);
            redisTemplate.delete(accountKey);
        } catch (Exception e) {
            log.warn("Redis failure during account failure state cleanup after successful login for hash [{}]: {}. Authentication proceeding normally.",
                    sha256Hex(normalizedEmail), e.getMessage());
        }
    }

    /**
     * Resolves client IP address strictly using server-side remote address.
     * Does not trust unverified X-Forwarded-For headers.
     *
     * @param request current HTTP request
     * @return client remote IP
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        return request.getRemoteAddr();
    }

    public String buildAccountKey(String normalizedEmail) {
        return ACCOUNT_KEY_PREFIX + sha256Hex(normalizedEmail);
    }

    public String buildIpKey(String clientIp) {
        return IP_KEY_PREFIX + clientIp;
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private Long getCount(String key) {
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return 0L;
        }
        try {
            long count = Long.parseLong(val.trim());
            if (count < 0) {
                throw new NumberFormatException("Negative counter value");
            }
            return count;
        } catch (Exception e) {
            log.warn("Malformed login rate-limit counter encountered in Redis");
            throw new IllegalStateException("Malformed login rate-limit counter in Redis", e);
        }
    }

    private void handleRedisException(String operation, Exception e) {
        log.error("Redis failure during login rate-limit operation [{}]: {}", operation, e.getMessage());
        if ("FAIL_CLOSED".equalsIgnoreCase(redisFailurePolicy)) {
            long fallbackRetryAfter = 60L;
            long resetTime = Instant.now().getEpochSecond() + fallbackRetryAfter;
            throw new RateLimitExceededException("Too many login attempts or service temporarily unavailable. Please try again later.", fallbackRetryAfter, resetTime);
        }
    }

    public int getAccountMaxAttempts() {
        return accountMaxAttempts;
    }

    public long getAccountWindowSeconds() {
        return accountWindowSeconds;
    }

    public int getIpMaxAttempts() {
        return ipMaxAttempts;
    }

    public long getIpWindowSeconds() {
        return ipWindowSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
