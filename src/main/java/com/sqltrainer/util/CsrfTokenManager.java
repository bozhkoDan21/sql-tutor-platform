package com.sqltrainer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер CSRF-токенов.
 * Генерирует уникальные токены для сессий преподавателя.
 */
public class CsrfTokenManager {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenManager.class);

    // Генератор случайных чисел
    private static final SecureRandom secureRandom = new SecureRandom();

    // Время жизни токена (30 минут)
    private static final long TOKEN_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    // Отслеживание времени создания токенов
    private static final ConcurrentHashMap<String, Long> tokenCreationTime = new ConcurrentHashMap<>();

    /**
     * Генерирует новый CSRF-токен для сессии.
     */
    public static String generateToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        // Генерируем случайный токен (32 байта = 256 бит)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Сохраняем в сессию
        session.setAttribute("csrfToken", token);
        tokenCreationTime.put(token, System.currentTimeMillis());

        log.debug("CSRF token generated for session: {}", session.getId());
        return token;
    }

    /**
     * Проверяет CSRF-токен.
     * @return true если токен валиден
     */
    public static boolean validateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.warn("CSRF validation failed: no session");
            return false;
        }

        String sessionToken = (String) session.getAttribute("csrfToken");
        if (sessionToken == null) {
            log.warn("CSRF validation failed: no token in session");
            return false;
        }

        // Проверяем, не истёк ли токен
        Long creationTime = tokenCreationTime.get(sessionToken);
        if (creationTime == null || System.currentTimeMillis() - creationTime > TOKEN_TTL_MS) {
            log.warn("CSRF validation failed: token expired");
            tokenCreationTime.remove(sessionToken);
            session.removeAttribute("csrfToken");
            return false;
        }

        // Получаем токен из запроса (из заголовка или параметра)
        String requestToken = getRequestToken(request);

        boolean valid = sessionToken.equals(requestToken);
        if (!valid) {
            log.warn("CSRF validation failed: token mismatch for session {}", session.getId());
        }

        return valid;
    }

    /**
     * Извлекает CSRF-токен из запроса.
     * Ищет в заголовке X-CSRF-Token или в параметре csrf_token.
     */
    private static String getRequestToken(HttpServletRequest request) {
        // Сначала ищем в заголовке (для AJAX-запросов)
        String token = request.getHeader("X-CSRF-Token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // Затем в параметрах запроса (для обычных форм)
        token = request.getParameter("csrf_token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * Инвалидирует токен при выходе из системы.
     */
    public static void invalidateToken(HttpSession session) {
        if (session == null) return;

        String token = (String) session.getAttribute("csrfToken");
        if (token != null) {
            tokenCreationTime.remove(token);
            session.removeAttribute("csrfToken");
            log.debug("CSRF token invalidated for session: {}", session.getId());
        }
    }

    /**
     * Очищает просроченные токены (вызывается периодически).
     */
    public static void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var iterator = tokenCreationTime.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() > TOKEN_TTL_MS) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("Cleaned {} expired CSRF tokens", removed);
        }
    }
}