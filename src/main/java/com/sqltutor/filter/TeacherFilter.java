package com.sqltutor.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Фильтр аутентификации преподавателя.
 * <p>
 * Защищает все страницы и API преподавателя, требуя наличия активной сессии
 * с атрибутом role = "teacher". Без авторизации пользователь перенаправляется
 * на страницу входа.
 * </p>
 *
 * <p><b>Защищаемые ресурсы:</b>
 * <ul>
 *   <li>/teacher.jsp — панель преподавателя</li>
 *   <li>/teacher/* — любые подпути преподавателя</li>
 *   <li>/api/teacher/* — API преподавателя</li>
 * </ul>
 * </p>
 */
@WebFilter({"/teacher.jsp", "/teacher/*", "/api/teacher/*"})
public class TeacherFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TeacherFilter.class);

    /**
     * Инициализация фильтра (вызывается контейнером при старте).
     *
     * @param filterConfig конфигурация фильтра
     * @throws ServletException при ошибках инициализации
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("TeacherFilter initialized");
    }

    /**
     * Проверяет авторизацию преподавателя.
     * <p>
     * Если пользователь имеет активную сессию с ролью "teacher" — пропускаем запрос.
     * Иначе — редирект на страницу входа /teacher-login.jsp.
     * </p>
     *
     * @param request  HTTP-запрос
     * @param response HTTP-ответ
     * @param chain    цепочка фильтров
     * @throws IOException      при ошибках ввода-вывода
     * @throws ServletException при ошибках сервлета
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);
        boolean isTeacher = (session != null && "teacher".equals(session.getAttribute("role")));

        if (isTeacher) {
            // Авторизован — пропускаем
            chain.doFilter(request, response);
        } else {
            // Не авторизован — редирект на логин
            log.warn("Unauthorized access attempt to: {}", req.getRequestURI());
            resp.sendRedirect(req.getContextPath() + "/teacher-login.jsp");
        }
    }

    /**
     * Уничтожение фильтра (вызывается контейнером при остановке).
     */
    @Override
    public void destroy() {
        log.info("TeacherFilter destroyed");
    }
}