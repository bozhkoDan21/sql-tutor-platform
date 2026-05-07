package com.sqltrainer.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фильтр для ограничения частоты запросов (rate limiting).
 *
 * Правила:
 * - Неавторизованные студенты: 1 запрос в секунду на IP (только для API)
 * - Преподаватели: без ограничений
 * - Статические ресурсы и информационные API: без ограничений
 */
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_SECOND = 1;
    private static final long WINDOW_MS = 1000;
    private static final Map<String, RequestData> requestData = new ConcurrentHashMap<>();

    // API, которые НЕ ограничиваем (информационные, не влияют на нагрузку)
    private static final String[] UNLIMITED_API_PATHS = {
            "/api/databases",   // список баз
            "/api/dbinfo",      // информация о БД (таблицы)
            "/api/columns",     // список колонок
            "/api/csrf/token"   // получение CSRF-токена
    };

    // Пути, которые полностью исключаем из rate limiting
    private static final String[] EXCLUDED_PATHS = {
            "/login", "/index", "/teacher",
            "/css/", "/js/", "/uploads/"
    };

    @Override
    public void init(FilterConfig filterConfig) {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                    long now = System.currentTimeMillis();
                    requestData.entrySet().removeIf(entry ->
                            now - entry.getValue().lastRequestTime > WINDOW_MS * 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        log.info("RateLimitFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        // 1. Пропускаем статические ресурсы
        if (isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Пропускаем информационные API (список баз, таблиц, колонок)
        if (isUnlimitedApi(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Проверяем, авторизован ли пользователь как преподаватель
        HttpSession session = req.getSession(false);
        boolean isTeacher = false;
        if (session != null && session.getAttribute("authenticated") != null) {
            Boolean auth = (Boolean) session.getAttribute("authenticated");
            isTeacher = auth != null && auth;
        }

        // 4. Преподавателей не ограничиваем
        if (isTeacher) {
            chain.doFilter(request, response);
            return;
        }

        // 5. Только для студентов применяем rate limiting к API запросам
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(req);

        if (isRateLimited(clientIp)) {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            resp.setStatus(429);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Слишком много запросов. Пожалуйста, подождите 1 секунду между запросами.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        RequestData data = requestData.get(ip);

        if (data == null) {
            requestData.put(ip, new RequestData(now, 1));
            return false;
        }

        synchronized (data) {
            if (now - data.lastRequestTime < WINDOW_MS) {
                if (data.requestCount >= MAX_REQUESTS_PER_SECOND) {
                    return true;
                }
                data.requestCount++;
                data.lastRequestTime = now;
                return false;
            } else {
                data.requestCount = 1;
                data.lastRequestTime = now;
                return false;
            }
        }
    }

    private boolean isExcludedPath(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.equals(excluded) || path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnlimitedApi(String path) {
        for (String api : UNLIMITED_API_PATHS) {
            if (path.startsWith(api)) {
                return true;
            }
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "localhost";
        }
        return ip;
    }

    private static class RequestData {
        long lastRequestTime;
        int requestCount;
        RequestData(long lastRequestTime, int requestCount) {
            this.lastRequestTime = lastRequestTime;
            this.requestCount = requestCount;
        }
    }

    @Override
    public void destroy() {
        log.info("RateLimitFilter destroyed");
    }
}