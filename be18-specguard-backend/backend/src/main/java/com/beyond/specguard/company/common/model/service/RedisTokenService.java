package com.beyond.specguard.company.common.model.service;

import com.beyond.specguard.common.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;

    // ================== Refresh Token 관리 ==================

    // 저장 (username 기준)
    public void saveRefreshToken(String username, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                appProperties.getRedis().getPrefix().getRefresh() + username,
                refreshToken,
                ttlSeconds,
                TimeUnit.SECONDS
        );
    }

    // 조회
    public String getRefreshToken(String username) {
        return redisTemplate.opsForValue().get(
                appProperties.getRedis().getPrefix().getRefresh() + username
        );
    }

    // 삭제
    public void deleteRefreshToken(String username) {
        redisTemplate.delete(
                appProperties.getRedis().getPrefix().getRefresh() + username
        );
    }

    // ================== Access Token 블랙리스트 관리 ==================

    // 블랙리스트 등록 (jti 기준)
    public void blacklistAccessToken(String jti, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                appProperties.getRedis().getPrefix().getBlacklist() + jti,
                "logout",
                ttlSeconds,
                TimeUnit.SECONDS
        );
    }

    // 블랙리스트 조회
    public boolean isBlacklisted(String jti) {
        return redisTemplate.hasKey(appProperties.getRedis().getPrefix().getBlacklist() + jti);
    }

    // ================== Access Token 세션 관리 (단일 세션) ==================

    // 저장 (username → AccessToken jti 매핑)
    public void saveUserSession(String email, String accessJti, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                appProperties.getRedis().getPrefix().getSession() + email,
                accessJti,
                ttlSeconds,
                TimeUnit.SECONDS
        );
    }
    // 조회 (username으로 현재 세션의 accessJti 확인)
    public String getUserSession(String email) {
        return redisTemplate.opsForValue().get(
                appProperties.getRedis().getPrefix().getSession() + email
        );
    }

    // 삭제 (username 기준 세션 삭제)
    public void deleteUserSession(String email) {
        redisTemplate.delete(
                appProperties.getRedis().getPrefix().getSession() + email
        );
    }
}

