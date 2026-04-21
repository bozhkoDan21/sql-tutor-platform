package com.sqltrainer.filter;

import com.sqltrainer.util.Constants;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Фильтр для установки кодировки UTF-8 и защитных HTTP-заголовков.
 * Обрабатывает все запросы к приложению.
 */
@WebFilter("/*")
public class EncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Инициализация
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Статические файлы пропускаем без изменений
        if (path.endsWith(".css") || path.endsWith(".js") ||
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".ico")) {
            chain.doFilter(req, resp);
            return;
        }

        // Устанавливаем UTF-8 для всех запросов и ответов
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=UTF-8");

        // Защитные заголовки для предотвращения XSS и кликджекинга
        resp.setHeader("Content-Security-Policy", Constants.CSP_POLICY);

        // Запрещает браузеру подменять MIME-тип
        resp.setHeader("X-Content-Type-Options", "nosniff");

        // Запрещает встраивание страницы в frame (защита от кликджекинга)
        resp.setHeader("X-Frame-Options", "DENY");

        // Включает встроенный XSS-фильтр браузера
        resp.setHeader("X-XSS-Protection", "1; mode=block");

        // Отключает кэширование для динамических страниц
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Expires", "0");

        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {
        // Очистка
    }
}