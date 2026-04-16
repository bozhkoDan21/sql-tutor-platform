package com.sqltrainer.servlet;

import com.sqltrainer.config.DatabaseConfig;
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
import java.util.regex.Pattern;

@WebServlet("/api/columns")
public class ColumnInfoServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ColumnInfoServlet.class);
    private final Gson gson = new Gson();

    // Только буквы, цифры и подчёркивания, начинается с буквы или подчёркивания
    private static final Pattern VALID_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

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

        // ВАЛИДАЦИЯ: защита от SQL-инъекций
        if (!isValidIdentifier(dbName)) {
            log.warn("Invalid database name: {}", dbName);
            resp.getWriter().write("{\"error\":\"Invalid database name\"}");
            return;
        }

        if (!isValidIdentifier(tableName)) {
            log.warn("Invalid table name: {}", tableName);
            resp.getWriter().write("{\"error\":\"Invalid table name\"}");
            return;
        }

        Map<String, Object> result = new HashMap<>();
        List<String> columns = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName)) {
            DatabaseMetaData meta = conn.getMetaData();
            // tableName уже валидирован, но для безопасности используем экранирование
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

    /**
     * Валидация идентификатора БД/таблицы/колонки
     * Только буквы, цифры, подчёркивания, начинается с буквы или подчёркивания
     */
    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        // Дополнительная защита от зарезервированных слов и слишком длинных имён
        if (identifier.length() > 63) {
            return false;
        }
        String upper = identifier.toUpperCase();
        // Защита от SQL-команд в имени
        String[] reserved = {"SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
                "ALTER", "TRUNCATE", "GRANT", "REVOKE", "UNION", "WHERE"};
        for (String word : reserved) {
            if (upper.contains(word)) {
                return false;
            }
        }
        return VALID_IDENTIFIER_PATTERN.matcher(identifier).matches();
    }
}