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
 * - Неавторизованные студенты: 1 запрос в секунду на сессию
 * - Преподаватели: без ограничений
 * - Статические ресурсы и информационные API: без ограничений
 *
 * В отличие от ограничения по IP, ограничение по сессии корректно работает
 * в условиях NAT (компьютерный класс), так как у каждого студента своя сессия.
 */
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_SECOND = 1;
    private static final long WINDOW_MS = 1000;

    // Хранилище данных о запросах по ID сессии (не по IP)
    private static final Map<String, RequestData> sessionRequestData = new ConcurrentHashMap<>();

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
        // Запускаем фоновый поток для очистки устаревших записей
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000); // Раз в минуту
                    long now = System.currentTimeMillis();
                    int removed = 0;

                    // Удаляем записи, которые не использовались дольше 2 минут
                    for (Map.Entry<String, RequestData> entry : sessionRequestData.entrySet()) {
                        if (now - entry.getValue().getLastRequestTime() > WINDOW_MS * 120) {
                            sessionRequestData.remove(entry.getKey());
                            removed++;
                        }
                    }

                    if (removed > 0) {
                        log.debug("RateLimitFilter cleanup: removed {} expired session records", removed);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        log.info("RateLimitFilter initialized (rate limiting by session ID, max {} req/sec)", MAX_REQUESTS_PER_SECOND);
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

        // 6. Получаем или создаём сессию для студента
        //    Важно: используем ту же сессию, что и для аутентификации (если есть)
        session = req.getSession(true); // создаём сессию, если её нет
        String sessionId = session.getId();

        if (isRateLimited(sessionId)) {
            log.warn("Rate limit exceeded for session: {} (user from IP: {}), path: {}",
                    sessionId, getClientIp(req), path);
            resp.setStatus(429);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Слишком много запросов. Пожалуйста, подождите 1 секунду между запросами.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Проверяет, не превышен ли лимит запросов для данной сессии.
     *
     * @param sessionId идентификатор сессии
     * @return true если лимит превышен
     */
    private boolean isRateLimited(String sessionId) {
        long now = System.currentTimeMillis();
        RequestData data = sessionRequestData.get(sessionId);

        if (data == null) {
            // Первый запрос от сессии
            sessionRequestData.put(sessionId, new RequestData(now, 1));
            return false;
        }

        synchronized (data) {
            if (now - data.getLastRequestTime() < WINDOW_MS) {
                // В пределах текущего окна
                if (data.getRequestCount() >= MAX_REQUESTS_PER_SECOND) {
                    return true; // Превышен лимит
                }
                data.setRequestCount(data.getRequestCount() + 1);
                data.setLastRequestTime(now);
                return false;
            } else {
                // Новое окно (прошла секунда)
                data.setRequestCount(1);
                data.setLastRequestTime(now);
                return false;
            }
        }
    }

    /**
     * Проверяет, исключён ли путь из rate limiting.
     *
     * @param path путь запроса
     * @return true если путь исключён
     */
    private boolean isExcludedPath(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.equals(excluded) || path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, относится ли путь к неограничиваемым API.
     *
     * @param path путь запроса
     * @return true если API не требует ограничения
     */
    private boolean isUnlimitedApi(String path) {
        for (String api : UNLIMITED_API_PATHS) {
            if (path.startsWith(api)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Получает реальный IP-адрес клиента (для логирования).
     * Учитывает заголовки X-Forwarded-For, Proxy-Client-IP и т.д.
     *
     * @param request HTTP-запрос
     * @return IP-адрес клиента
     */
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

    /**
     * Класс для хранения данных о запросах от сессии.
     */
    private static class RequestData {
        private volatile long lastRequestTime;
        private volatile int requestCount;

        RequestData(long lastRequestTime, int requestCount) {
            this.lastRequestTime = lastRequestTime;
            this.requestCount = requestCount;
        }

        public long getLastRequestTime() { return lastRequestTime; }
        public void setLastRequestTime(long lastRequestTime) { this.lastRequestTime = lastRequestTime; }
        public int getRequestCount() { return requestCount; }
        public void setRequestCount(int requestCount) { this.requestCount = requestCount; }
    }

    @Override
    public void destroy() {
        sessionRequestData.clear();
        log.info("RateLimitFilter destroyed");
    }
}