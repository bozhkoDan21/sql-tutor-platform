package com.sqltutor.config;

import java.io.InputStream;
import java.util.Properties;
import java.sql.SQLException;

public class DatabaseConfig {
    private static final Properties props = new Properties();

    // Переменные окружения для подключения к БД
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");

    // Роли
    public enum Role {
        ADMIN, TEACHER, STUDENT
    }

    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            props.load(input);
            Class.forName("org.postgresql.Driver");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    // Админское подключение (создание баз)
    public static String getAdminUrl() {
        String propUrl = props.getProperty("db.admin.url", null);
        if (propUrl != null && !propUrl.contains("${")) {
            return propUrl;
        }
        // Если в properties используется localhost, заменяем на DB_HOST из окружения
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    }

    public static String getAdminUser() {
        return props.getProperty("db.admin.user", "postgres");
    }

    public static String getAdminPassword() {
        String propPass = props.getProperty("db.admin.password", "postgres");
        // Если пароль задан через переменную окружения
        String envPass = System.getenv("DB_ADMIN_PASSWORD");
        return envPass != null ? envPass : propPass;
    }

    // Подключение для преподавателя
    public static String getTeacherUrl(String dbName) {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + dbName;
    }

    public static String getTeacherUser() {
        return props.getProperty("db.teacher.user", "teacher_role");
    }

    public static String getTeacherPassword() {
        return props.getProperty("db.teacher.password", "teacher_pass");
    }

    // Подключение для студента
    public static String getStudentUrl(String dbName) {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + dbName;
    }

    public static String getTeacherSecret() {
        String envSecret = System.getenv("TEACHER_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            return envSecret;
        }
        return props.getProperty("teacher.secret", "teacher123");
    }

    // Получение подключения в зависимости от роли
    public static java.sql.Connection getConnection(Role role, String dbName, String studentName)
            throws SQLException {

        String url;
        String user;
        String password;

        switch (role) {
            case ADMIN:
                url = getAdminUrl();
                user = getAdminUser();
                password = getAdminPassword();
                break;
            case TEACHER:
                url = getTeacherUrl(dbName);
                user = getTeacherUser();
                password = getTeacherPassword();
                break;
            case STUDENT:
                if (dbName == null || dbName.isEmpty()) {
                    throw new SQLException("Database name is required for STUDENT connection");
                }
                url = getStudentUrl(dbName);
                user = "students";
                password = "student_pass";
                break;
            default:
                throw new IllegalArgumentException("Unknown role");
        }

        return java.sql.DriverManager.getConnection(url, user, password);
    }

    // Метод для получения таймаута
    public static int getQueryTimeout() {
        return Integer.parseInt(props.getProperty("db.query.timeout", "3"));
    }

    // Метод для получения максимального количества строк
    public static int getMaxRows() {
        return Integer.parseInt(props.getProperty("db.max.rows", "1000"));
    }

    // Метод для получения хоста (для отладки)
    public static String getDbHost() {
        return DB_HOST;
    }

    // Метод для получения порта (для отладки)
    public static String getDbPort() {
        return DB_PORT;
    }
}