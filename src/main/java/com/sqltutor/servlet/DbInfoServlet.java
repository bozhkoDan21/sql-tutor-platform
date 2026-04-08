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
 * Сервлет для получения информации о структуре выбранной базы данных.
 * <p>
 * Возвращает список всех пользовательских таблиц в схеме public указанной базы.
 * Используется на главной странице студента для отображения схемы данных.
 * </p>
 *
 * <p>Пример ответа:
 * <pre>
 * {
 *   "success": true,
 *   "dbName": "university_db",
 *   "tables": ["student", "enrollment", "faculty"],
 *   "tableCount": 3
 * }
 * </pre>
 * </p>
 *
 * @author SQL Trainer Team
 * @see DatabaseListServlet
 * @see StudentServlet
 */
@WebServlet("/api/dbinfo")
public class DbInfoServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DbInfoServlet.class);
    private final Gson gson = new Gson();

    /**
     * Обрабатывает GET-запрос и возвращает JSON-список таблиц в указанной базе данных.
     * <p>
     * Параметры запроса:
     * <ul>
     *   <li><b>db</b> — имя базы данных (обязательный)</li>
     * </ul>
     * </p>
     * <p>
     * Исключаются системные таблицы PostgreSQL (начинающиеся с pg_ или sql_).
     * </p>
     *
     * @param req  HTTP-запрос с параметром db
     * @param resp HTTP-ответ с JSON-данными
     * @throws IOException при ошибках ввода-вывода
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("db");

        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name is required\"}");
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
}