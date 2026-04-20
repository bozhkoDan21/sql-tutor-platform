package com.sqltrainer.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Утилита для работы с JWT токенами.
 *
 * Параметры токенов:
 * - Access Token: 1 час жизни (можно настроить)
 * - Refresh Token: 7 дней жизни
 * - Inactive timeout: 3 часа бездействия
 */
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    // ИСПРАВЛЕНО: без дефолтного значения, только из переменных окружения
    private static final String SECRET;
    private static final SecretKey KEY;

    static {
        SECRET = System.getenv("JWT_SECRET");
        if (SECRET == null || SECRET.length() < 32) {
            String error = "JWT_SECRET must be set in environment variables (min 32 chars). " +
                    "Generate with: openssl rand -base64 32";
            log.error(error);
            throw new RuntimeException(error);
        }
        KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Util initialized successfully");
    }

    private static final long ACCESS_TOKEN_EXPIRY_HOURS = Long.parseLong(
            System.getenv().getOrDefault("JWT_ACCESS_TOKEN_EXPIRY_HOURS", "1"));
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = Long.parseLong(
            System.getenv().getOrDefault("JWT_REFRESH_TOKEN_EXPIRY_DAYS", "7"));
    private static final long INACTIVE_TIMEOUT_HOURS = Long.parseLong(
            System.getenv().getOrDefault("JWT_INACTIVE_TIMEOUT_HOURS", "3"));

    private static final long ACCESS_TOKEN_EXPIRY_MS = ACCESS_TOKEN_EXPIRY_HOURS * 3600000;
    private static final long REFRESH_TOKEN_EXPIRY_MS = REFRESH_TOKEN_EXPIRY_DAYS * 86400000;
    public static final long INACTIVE_TIMEOUT_MS = INACTIVE_TIMEOUT_HOURS * 3600000;

    /**
     * Генерирует access token
     * @param userId ID пользователя
     * @param role роль (student/teacher)
     * @param login логин
     * @return JWT токен
     */
    public static String generateAccessToken(Long userId, String role, String login) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .claim("login", login)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Генерирует refresh token
     * @param userId ID пользователя
     * @param role роль (student/teacher)
     * @return JWT токен
     */
    public static String generateRefreshToken(Long userId, String role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY_MS))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Проверяет и парсит токен
     * @param token JWT токен
     * @return Claims из токена
     * @throws JwtException если токен невалидный
     */
    public static Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Проверяет валидность токена (не истёк, корректная подпись)
     * @param token JWT токен
     * @return true если валиден
     */
    public static boolean isValidToken(String token) {
        try {
            Claims claims = parseToken(token);
            // Проверка, что это не refresh token
            if ("refresh".equals(claims.get("type"))) {
                return false;
            }
            // Проверка, что не истёк
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет валидность refresh token
     */
    public static boolean isValidRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type")) && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает userId из токена
     */
    public static Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * Получает роль из токена
     */
    public static String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Получает login из токена
     */
    public static String getLoginFromToken(String token) {
        return parseToken(token).get("login", String.class);
    }

    /**
     * Получает время истечения токена
     */
    public static Date getExpirationFromToken(String token) {
        return parseToken(token).getExpiration();
    }

    /**
     * Проверяет, не истёк ли таймаут бездействия
     * @param lastActivityTime время последней активности
     * @return true если активность не истекла
     */
    public static boolean isWithinInactiveTimeout(Date lastActivityTime) {
        if (lastActivityTime == null) return true;
        return System.currentTimeMillis() - lastActivityTime.getTime() < INACTIVE_TIMEOUT_MS;
    }
}