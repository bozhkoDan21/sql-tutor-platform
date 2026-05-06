package com.sqltrainer.servlet.auth;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервлет для аутентификации преподавателя по паролю.
 */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);
    private final Gson gson = new Gson();

    // Пароль преподавателя из переменных окружения
    private static final String TEACHER_PASSWORD = System.getenv().getOrDefault("TEACHER_PASSWORD", "teacher123");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> loginData = gson.fromJson(body, Map.class);

        String password = loginData.get("password");

        Map<String, Object> response = new HashMap<>();

        if (password == null || password.isEmpty()) {
            response.put("success", false);
            response.put("error", "Password is required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (TEACHER_PASSWORD.equals(password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("authenticated", true);
            session.setMaxInactiveInterval(3600); // 1 час

            response.put("success", true);
            response.put("message", "Login successful");
            log.info("Teacher logged in successfully");
        } else {
            response.put("success", false);
            response.put("error", "Invalid password");
            log.warn("Failed login attempt with wrong password");
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Проверка статуса аутентификации
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", session != null && session.getAttribute("authenticated") != null);
        response.put("success", true);

        resp.getWriter().write(gson.toJson(response));
    }
}