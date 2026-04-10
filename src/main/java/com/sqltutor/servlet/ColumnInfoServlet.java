package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Сервлет для получения информации о колонках таблицы.
 * Используется для автодополнения в редакторе SQL.
 */
@WebServlet("/api/columns")
public class ColumnInfoServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ColumnInfoServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("db");
        String tableName = req.getParameter("table");

        if (dbName == null || dbName.isEmpty() || tableName == null || tableName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name and table name are required\"}");
            return;
        }

        Map<String, Object> result = new HashMap<>();
        List<String> columns = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName)) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, "public", tableName, "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
            result.put("success", true);
            result.put("columns", columns);
            log.debug("Loaded {} columns from {}.{}", columns.size(), dbName, tableName);
        } catch (SQLException e) {
            log.error("Failed to get columns for {}.{}: {}", dbName, tableName, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(result));
    }
}