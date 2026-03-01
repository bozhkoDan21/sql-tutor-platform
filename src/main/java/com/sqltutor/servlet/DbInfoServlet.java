package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/dbinfo")
public class DbInfoServlet extends HttpServlet {

    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("db");

        // Проверяем, что имя базы передано
        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name is required\"}");
            return;
        }

        Map<String, Object> result = new HashMap<>();
        List<String> tables = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.STUDENT, dbName, null)) {

            // Получаем список таблиц
            DatabaseMetaData meta = conn.getMetaData();
            String[] types = {"TABLE"};

            try (ResultSet rs = meta.getTables(null, "public", "%", types)) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            result.put("dbName", dbName);
            result.put("tables", tables);
            result.put("success", true);

        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(result));
    }
}