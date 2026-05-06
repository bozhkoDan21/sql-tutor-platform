package com.sqltrainer.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Фильтр для ограничения доступа к API преподавателя.
 * Проверяет наличие аутентифицированной сессии преподавателя.
 */
@WebFilter({"/api/teacher/*"})  // Только API, JSP страницы не трогаем
@SuppressWarnings("unused")  // Класс используется через аннотацию @WebFilter
public class TeacherFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TeacherFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("TeacherFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);

        // Проверяем, есть ли сессия и аутентифицирован ли преподаватель
        if (session == null || session.getAttribute("authenticated") == null) {
            log.warn("Unauthorized access to teacher API: {}", req.getRequestURI());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Unauthorized. Please login as teacher.\"}");
            return;
        }

        Boolean isAuthenticated = (Boolean) session.getAttribute("authenticated");
        if (!isAuthenticated) {
            log.warn("Invalid teacher session: {}", req.getRequestURI());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"error\":\"Invalid session. Please login again.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("TeacherFilter destroyed");
    }
}