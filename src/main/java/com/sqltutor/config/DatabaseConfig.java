package com.sqltutor.config;

import java.io.InputStream;
import java.util.Properties;
import java.sql.SQLException;  // ← добавить этот импорт

public class DatabaseConfig {
    private static final Properties props = new Properties();

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
        return props.getProperty("db.admin.url", "jdbc:postgresql://localhost:5432/postgres");
    }

    public static String getAdminUser() {
        return props.getProperty("db.admin.user", "postgres");
    }

    public static String getAdminPassword() {
        return props.getProperty("db.admin.password", "postgres");
    }

    // Подключение для преподавателя
    public static String getTeacherUrl(String dbName) {
        return "jdbc:postgresql://localhost:5432/" + dbName;
    }

    public static String getTeacherUser() {
        return props.getProperty("db.teacher.user", "teacher_role");
    }

    public static String getTeacherPassword() {
        return props.getProperty("db.teacher.password", "teacher_pass");
    }

    // Подключение для студента
    public static String getStudentUrl(String dbName) {
        return "jdbc:postgresql://localhost:5432/" + dbName;
    }

    public static String getTeacherSecret() {
        // Сначала пробуем из переменной окружения
        String envSecret = System.getenv("TEACHER_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            return envSecret;
        }
        // Если нет - из properties
        return props.getProperty("teacher.secret", "teacher123");
    }

    // Получение подключения в зависимости от роли
    public static java.sql.Connection getConnection(Role role, String dbName, String studentName)
            throws SQLException {  // ← теперь компилятор знает, что такое SQLException

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
                // dbName должен быть передан обязательно
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
}