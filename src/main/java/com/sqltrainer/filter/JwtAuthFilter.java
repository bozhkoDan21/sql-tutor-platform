package com.sqltrainer.filter;

import com.sqltrainer.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT аутентификационный фильтр.
 * Проверяет наличие и валидность JWT токена в заголовке Authorization.
 * Для публичных эндпоинтов аутентификация не требуется.
 */
@WebFilter("/api/*")
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Эндпоинты, доступные без аутентификации
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/refresh"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Публичные эндпоинты пропускаем без токена
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Получаем токен из заголовка Authorization: Bearer <token>
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(resp, "Missing or invalid token");
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (!JwtUtil.isValidToken(token)) {
                sendUnauthorized(resp, "Token expired or invalid");
                return;
            }

            Long userId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);
            String login = JwtUtil.getLoginFromToken(token);

            // Сохраняем данные пользователя в атрибутах запроса для дальнейшего использования
            req.setAttribute("userId", userId);
            req.setAttribute("role", role);
            req.setAttribute("login", login);

            chain.doFilter(request, response);

        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            sendUnauthorized(resp, "Invalid token");
        } catch (Exception e) {
            log.error("Auth filter error: {}", e.getMessage());
            sendUnauthorized(resp, "Authentication error");
        }
    }

    /**
     * Проверяет, является ли запрошенный путь публичным эндпоинтом.
     */
    private boolean isPublicEndpoint(String path) {
        for (String endpoint : PUBLIC_ENDPOINTS) {
            if (path.startsWith(endpoint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Отправляет ответ с кодом 401 Unauthorized.
     */
    private void sendUnauthorized(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}