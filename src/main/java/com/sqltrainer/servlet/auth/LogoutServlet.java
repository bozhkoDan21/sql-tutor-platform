package com.sqltrainer.servlet.auth;

import com.google.gson.Gson;
import com.sqltrainer.util.CsrfTokenManager;
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
 * Сервлет для выхода преподавателя из системы.
 */
@WebServlet("/api/logout")
public class LogoutServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LogoutServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        if (session != null) {
            CsrfTokenManager.invalidateToken(session);
            session.invalidate();
            log.info("Teacher logged out");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        resp.getWriter().write(gson.toJson(response));
    }
}