package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.sqltutor.util.QueryExecutor;
import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private Gson gson = new Gson();

    public static ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    public static class SessionInfo {
        private String sessionId;
        private String lastQuery;
        private Date lastAccess;

        public SessionInfo(String sessionId, String lastQuery) {
            this.sessionId = sessionId;
            this.lastQuery = lastQuery;
            this.lastAccess = new Date();
        }

        public String getSessionId() { return sessionId; }
        public String getLastQuery() { return lastQuery; }
        public Date getLastAccess() { return lastAccess; }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Получаем параметры
        String dbName = req.getParameter("database");
        String query = req.getParameter("query");

        // Проверяем, что имя базы передано
        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name is required\"}");
            return;
        }

        if (query == null || query.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Query is required\"}");
            return;
        }

        // Получаем или создаем сессию
        HttpSession session = req.getSession(true);
        String sessionId = session.getId();

        // Сохраняем в активные сессии
        activeSessions.put(sessionId, new SessionInfo(sessionId, query));

        // Очищаем старые сессии (старше 30 минут)
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getLastAccess().getTime() < System.currentTimeMillis() - 30*60*1000);

        // Логируем запрос
        System.out.println("[" + new Date() + "] DB: " + dbName + " Session: " + sessionId + " - " + query);

        // Выполняем запрос
        QueryExecutor.QueryResult result = QueryExecutor.executeAsStudent(dbName, query);

        resp.getWriter().write(gson.toJson(result));
    }
}