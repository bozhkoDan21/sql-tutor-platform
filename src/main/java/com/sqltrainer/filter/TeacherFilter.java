package com.sqltrainer.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter({"/api/teacher/*"})
public class TeacherFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TeacherFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("TeacherFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String role = (String) req.getAttribute("role");

        if (role == null) {
            log.warn("Unauthorized access to teacher API: {}", req.getRequestURI());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        if (!"teacher".equals(role)) {
            log.warn("Access denied: teacher role required for {}", req.getRequestURI());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"Teacher role required\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("TeacherFilter destroyed");
    }
}