package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/api/teacher")
@MultipartConfig(
        maxFileSize = 1024 * 1024 * 10,      // 10 MB
        maxRequestSize = 1024 * 1024 * 15    // 15 MB
)
public class TeacherServlet extends HttpServlet {

    private Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        try {
            // Преподаватель подключается под своей ролью
            try (Connection conn = DatabaseConfig.getConnection(
                    DatabaseConfig.Role.TEACHER, "postgres", null)) {

                if ("upload".equals(action)) {
                    String dbName = req.getParameter("dbName");
                    Part filePart = req.getPart("sqlFile");
                    handleUpload(conn, dbName, filePart, response);

                } else if ("list".equals(action)) {
                    response.put("databases", getDatabaseList(conn));

                } else if ("delete".equals(action)) {
                    String dbName = req.getParameter("dbName");
                    handleDelete(conn, dbName, response);

                } else if ("sessions".equals(action)) {
                    // Получаем активные сессии из StudentServlet
                    List<StudentServlet.SessionInfo> sessions =
                            new ArrayList<>(StudentServlet.activeSessions.values());
                    response.put("sessions", sessions);
                }
            }

        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doPost(req, resp);
    }

    private void handleUpload(Connection conn, String dbName, Part filePart,
                              Map<String, Object> response)
            throws IOException, SQLException {

        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        if (filePart == null || filePart.getSize() == 0) {
            response.put("error", "SQL file is required");
            return;
        }

        // Читаем SQL скрипт
        String script = readSqlScript(filePart);

        try (Statement stmt = conn.createStatement()) {
            // Создаем базу данных
            stmt.executeUpdate("CREATE DATABASE " + dbName + " OWNER teacher_role");
        }

        // Подключаемся к новой базе и выполняем скрипт
        try (Connection dbConn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.TEACHER, dbName, null);
             Statement stmt = dbConn.createStatement()) {

            executeSqlScript(stmt, script);
        }

        response.put("success", true);
        response.put("message", "Database '" + dbName + "' created");
    }

    private void handleDelete(Connection conn, String dbName,
                              Map<String, Object> response) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            response.put("success", true);
        }
    }

    private String readSqlScript(Part filePart) throws IOException {
        StringBuilder script = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(filePart.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                script.append(line).append("\n");
            }
        }
        return script.toString();
    }

    private void executeSqlScript(Statement stmt, String script) throws SQLException {
        String[] queries = script.split(";");
        for (String query : queries) {
            if (!query.trim().isEmpty()) {
                stmt.execute(query);
            }
        }
    }

    private List<String> getDatabaseList(Connection conn) throws SQLException {
        List<String> databases = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT datname FROM pg_database " +
                             "WHERE datistemplate = false AND datname NOT IN ('postgres')")) {
            while (rs.next()) {
                databases.add(rs.getString("datname"));
            }
        }
        return databases;
    }
}