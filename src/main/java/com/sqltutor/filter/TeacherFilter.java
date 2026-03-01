package com.sqltutor.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter({"/teacher.jsp", "/api/teacher/*"})
public class TeacherFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Метод init должен быть реализован
        System.out.println("TeacherFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);

        // Проверяем, есть ли сессия с ролью teacher
        if (session != null && "teacher".equals(session.getAttribute("role"))) {
            chain.doFilter(request, response); // доступ разрешен
        } else {
            // Нет доступа - редирект на страницу логина
            resp.sendRedirect("teacher-login.jsp");
        }
    }

    @Override
    public void destroy() {
        // Метод destroy должен быть реализован
        System.out.println("TeacherFilter destroyed");
    }
}