package com.sqltrainer.servlet.database;

import com.sqltrainer.config.DatabaseConfig;
import com.google.gson.Gson;
import com.sqltrainer.util.Constants;
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

/**
 * Сервлет для получения информации о базе данных.
 * Возвращает список таблиц в указанной базе данных.
 */
@WebServlet("/api/dbinfo")
public class DbInfoServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DbInfoServlet.class);
    private final Gson gson = new Gson();

    private static final Pattern VALID_DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("db");

        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name is required\"}");
            return;
        }

        // Валидация имени базы данных
        if (!isValidDatabaseName(dbName)) {
            log.warn("Invalid database name: {}", dbName);
            resp.getWriter().write("{\"error\":\"Invalid database name\"}");
            return;
        }

        Map<String, Object> result = new HashMap<>();
        List<String> tables = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName)) {
            DatabaseMetaData meta = conn.getMetaData();
            String[] types = {"TABLE"};

            try (ResultSet rs = meta.getTables(null, "public", "%", types)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Исключаем системные таблицы PostgreSQL
                    if (!tableName.startsWith("pg_") && !tableName.startsWith("sql_")) {
                        tables.add(tableName);
                    }
                }
            }

            Collections.sort(tables);

            result.put("dbName", dbName);
            result.put("tables", tables);
            result.put("tableCount", tables.size());
            result.put("success", true);

            log.debug("Loaded {} tables from database {}", tables.size(), dbName);

        } catch (SQLException e) {
            log.error("Failed to load db info for {}: {}", dbName, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(result));
    }

    /**
     * Проверяет корректность имени базы данных.
     * Запрещает системные базы данных PostgreSQL.
     */
    private boolean isValidDatabaseName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() > 63) {
            return false;
        }
        // Защита от системных баз данных
        String[] systemDbs = Constants.SYSTEM_DATABASES;
        for (String systemDb : systemDbs) {
            if (name.equalsIgnoreCase(systemDb)) {
                return false;
            }
        }
        return VALID_DB_NAME_PATTERN.matcher(name).matches();
    }
}