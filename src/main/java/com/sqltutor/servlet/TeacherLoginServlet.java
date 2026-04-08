package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Сервлет аутентификации преподавателя.
 * <p>
 * Проверяет введённый пароль с секретным ключом из конфигурации.
 * При успешной аутентификации создаёт сессию с ролью "teacher".
 * </p>
 *
 * <p>Доступ к панели преподавателя защищён {@link com.sqltutor.filter.TeacherFilter}.</p>
 *
 * @author SQL Trainer Team
 * @see DatabaseConfig#getTeacherSecret()
 * @see com.sqltutor.filter.TeacherFilter
 */
@WebServlet("/teacher-login")
public class TeacherLoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherLoginServlet.class);

    /**
     * Обрабатывает POST-запрос на вход в панель преподавателя.
     * <p>
     * Параметры запроса:
     * <ul>
     *   <li><b>password</b> — пароль доступа (обязательный)</li>
     * </ul>
     * </p>
     * <p>
     * При успешном входе создаётся сессия с атрибутом role="teacher"
     * и тайм-аутом 8 часов. При ошибке — редирект обратно на страницу входа.
     * </p>
     *
     * @param req  HTTP-запрос с параметром password
     * @param resp HTTP-ответ (редирект на teacher.jsp или обратно на логин)
     * @throws IOException при ошибках ввода-вывода
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String password = req.getParameter("password");
        String teacherSecret = DatabaseConfig.getTeacherSecret();

        if (teacherSecret != null && teacherSecret.equals(password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("role", "teacher");
            session.setMaxInactiveInterval(8 * 60 * 60); // 8 часов

            log.info("Teacher logged in successfully from IP: {}", req.getRemoteAddr());
            resp.sendRedirect("teacher.jsp");
        } else {
            log.warn("Failed teacher login attempt from IP: {}", req.getRemoteAddr());
            resp.sendRedirect("teacher-login.jsp?error=1");
        }
    }
}