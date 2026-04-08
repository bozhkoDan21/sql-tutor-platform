package com.sqltutor.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Сервлет для выхода пользователя из системы.
 * <p>
 * Уничтожает текущую сессию пользователя и перенаправляет на главную страницу.
 * Доступен как для студентов, так и для преподавателей.
 * </p>
 *
 * <p>После выхода все данные сессии (роль, авторизация) удаляются,
 * и пользователь возвращается к неавторизованному состоянию.</p>
 *
 * @author SQL Trainer Team
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    /**
     * Обрабатывает GET-запрос на выход.
     * <p>
     * Если существует активная сессия, она аннулируется (invalidate).
     * После чего пользователь перенаправляется на index.jsp.
     * </p>
     *
     * @param req  HTTP-запрос
     * @param resp HTTP-ответ (редирект на главную)
     * @throws IOException при ошибках ввода-вывода
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        resp.sendRedirect("index.jsp");
    }
}