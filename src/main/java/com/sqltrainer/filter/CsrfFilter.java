package com.sqltrainer.filter;

import com.sqltrainer.util.CsrfTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Фильтр для защиты от CSRF-атак.
 * Проверяет наличие и корректность CSRF-токена для всех модифицирующих запросов.
 */
public class CsrfFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CsrfFilter.class);

    // Методы, которые не требуют CSRF-защиты (только GET и OPTIONS)
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    // URL, которые не требуют CSRF-защиты (логин, логаут)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/login",
            "/api/logout",
            "/api/database/verify"  // Проверка пароля БД — не требует CSRF
    );

    // Статические ресурсы
    private static final Set<String> STATIC_PATHS = Set.of(
            "/css/", "/js/", "/uploads/"
    );

    // JSP страницы (не требуют CSRF, но формы на них должны содержать токен)
    private static final Set<String> JSP_PATHS = Set.of(
            "/index", "/teacher", "/login", "/pages/"
    );

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("CSRF Filter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Пропускаем статические ресурсы
        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Безопасные методы (GET, HEAD, OPTIONS) не требуют CSRF-проверки
        if (SAFE_METHODS.contains(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Исключаем определённые пути (логин и т.д.)
        if (isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Только для преподавателя (защищённые API)
        if (!isTeacherApi(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Проверяем CSRF-токен
        if (!CsrfTokenManager.validateToken(req)) {
            log.warn("CSRF validation failed for {} from IP: {}", path, req.getRemoteAddr());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Ошибка CSRF-проверки. Пожалуйста, обновите страницу и попробуйте снова.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Проверяет, является ли URL API преподавателя (требует CSRF-защиты).
     */
    private boolean isTeacherApi(String path) {
        return path.startsWith("/api/teacher") ||
                path.startsWith("/api/teacher/") ||
                "/api/teacher".equals(path);
    }

    /**
     * Проверяет, является ли URL статическим ресурсом.
     */
    private boolean isStaticResource(String path) {
        for (String staticPath : STATIC_PATHS) {
            if (path.contains(staticPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, исключён ли URL из CSRF-проверки.
     */
    private boolean isExcludedPath(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.equals(excluded)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        log.info("CSRF Filter destroyed");
    }
}