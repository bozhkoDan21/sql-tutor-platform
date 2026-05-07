package com.sqltrainer.servlet.csrf;

import com.sqltrainer.util.CsrfTokenManager;
import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервлет для получения CSRF-токена.
 * Доступен только для аутентифицированных преподавателей.
 */
@WebServlet("/api/csrf/token")
public class CsrfTokenServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        Map<String, Object> response = new HashMap<>();

        // Проверяем, авторизован ли преподаватель
        if (session == null || session.getAttribute("authenticated") == null) {
            response.put("success", false);
            response.put("error", "Не авторизован");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // Получаем или генерируем CSRF-токен
        String token = (String) session.getAttribute("csrfToken");
        if (token == null) {
            token = CsrfTokenManager.generateToken(session);
        }

        response.put("success", true);
        response.put("token", token);
        resp.getWriter().write(gson.toJson(response));
    }
}