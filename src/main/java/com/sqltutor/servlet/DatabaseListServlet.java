package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/databases")
public class DatabaseListServlet extends HttpServlet {

    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();
        List<String> databases = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.STUDENT, "postgres", null)) {

            String sql = "SELECT datname FROM pg_database " +
                    "WHERE datistemplate = false " +
                    "AND datname NOT IN ('postgres', 'template0', 'template1') " +
                    "ORDER BY datname";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    databases.add(rs.getString("datname"));
                }
            }

            response.put("success", true);
            response.put("databases", databases);

        } catch (SQLException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }
}