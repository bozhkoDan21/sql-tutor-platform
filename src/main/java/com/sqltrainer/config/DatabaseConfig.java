package com.sqltrainer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Конфигурация подключений к базе данных.
 * Использует HikariCP для пулинга соединений.
 * Поддерживает три роли: ADMIN, TEACHER, STUDENT.
 * Для каждой учебной базы создаётся отдельный пул соединений.
 *
 * Максимальное количество учебных баз данных ограничено (MAX_DATABASES)
 * для предотвращения OutOfMemoryError при создании сотен пулов.
 */
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final HikariDataSource adminDataSource;
    private static final Map<String, HikariDataSource> studentDataSources = new ConcurrentHashMap<>();
    private static final Map<String, HikariDataSource> teacherDataSources = new ConcurrentHashMap<>();

    // ===== ЛИМИТЫ ДЛЯ ЗАЩИТЫ ОТ ПЕРЕГРУЗКИ =====

    /** Максимальное количество учебных баз данных (предотвращает создание тысяч пулов) */
    private static final int MAX_DATABASES = 50;

    /** Максимальное количество пулов студентов (не может превышать MAX_DATABASES) */
    private static final int MAX_STUDENT_POOLS = MAX_DATABASES;

    /** Максимальное количество пулов преподавателей (не может превышать MAX_DATABASES) */
    private static final int MAX_TEACHER_POOLS = MAX_DATABASES;

    // Параметры подключения из переменных окружения
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String ADMIN_USER = System.getenv().getOrDefault("DB_ADMIN_USER", "postgres");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("DB_ADMIN_PASSWORD", "postgres");
    private static final String TEACHER_USER = System.getenv().getOrDefault("DB_TEACHER_USER", "teacher_role");
    private static final String TEACHER_PASSWORD = System.getenv().getOrDefault("DB_TEACHER_PASSWORD", "teacher_pass");
    private static final String STUDENT_USER = System.getenv().getOrDefault("DB_STUDENT_USER", "students");
    private static final String STUDENT_PASSWORD = System.getenv().getOrDefault("DB_STUDENT_PASSWORD", "student_pass");

    // Ограничения для безопасности
    private static final int QUERY_TIMEOUT_SEC = Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT_SEC", "30"));
    private static final int CONNECTION_TIMEOUT_MS = Integer.parseInt(System.getenv().getOrDefault("CONNECTION_TIMEOUT_MS", "5000"));

    static {
        try {
            Class.forName("org.postgresql.Driver");
            log.info("PostgreSQL JDBC Driver loaded successfully");
        } catch (ClassNotFoundException e) {
            log.error("Failed to load PostgreSQL JDBC Driver", e);
            throw new RuntimeException("PostgreSQL JDBC Driver not found", e);
        }

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%s/postgres?useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8",
                DB_HOST, DB_PORT
        ));
        adminConfig.setUsername(ADMIN_USER);
        adminConfig.setPassword(ADMIN_PASSWORD);
        adminConfig.setMaximumPoolSize(2);
        adminConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        adminConfig.setInitializationFailTimeout(30000);
        adminConfig.setConnectionTestQuery("SELECT 1");
        adminConfig.setPoolName("admin-pool");
        adminConfig.addDataSourceProperty("stringtype", "unspecified");

        adminDataSource = new HikariDataSource(adminConfig);
        log.info("DatabaseConfig initialized with PostgreSQL at {}:{}", DB_HOST, DB_PORT);
        log.info("Max databases limit: {}", MAX_DATABASES);
    }

    /**
     * Роли пользователей для разграничения доступа к БД.
     */
    public enum Role {
        ADMIN,   // Полный доступ, только для служебных операций
        TEACHER, // Создание и управление учебными базами
        STUDENT  // Только чтение SELECT
    }

    /**
     * Возвращает соединение с БД в зависимости от роли и имени базы.
     * @param role роль пользователя
     * @param dbName имя базы данных (обязательно для STUDENT и TEACHER)
     */
    public static Connection getConnection(Role role, String dbName) throws SQLException {
        if (role == Role.STUDENT && (dbName == null || dbName.isEmpty())) {
            throw new SQLException("Для подключения студента требуется указать имя базы данных");
        }

        switch (role) {
            case ADMIN:
                return adminDataSource.getConnection();
            case TEACHER:
                return getTeacherDataSource(dbName).getConnection();
            case STUDENT:
                Connection conn = getStudentDataSource(dbName).getConnection();
                applyStudentLimits(conn);
                return conn;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    /**
     * Создаёт или возвращает существующий пул соединений для преподавателя.
     * Пул создаётся для каждой базы данных отдельно.
     *
     * @throws RuntimeException если превышен лимит количества учебных баз данных
     */
    private static HikariDataSource getTeacherDataSource(String dbName) {
        // Проверка лимита перед созданием нового пула
        if (!teacherDataSources.containsKey(dbName) && teacherDataSources.size() >= MAX_TEACHER_POOLS) {
            String error = String.format(
                    "Превышен лимит количества учебных баз данных (%d). Невозможно создать пул для '%s'",
                    MAX_TEACHER_POOLS, dbName);
            log.error(error);
            throw new RuntimeException(error);
        }

        return teacherDataSources.computeIfAbsent(dbName, name -> {
            log.info("Creating teacher pool for database: {} (current pools: {}/{})",
                    name, teacherDataSources.size() + 1, MAX_TEACHER_POOLS);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format(
                    "jdbc:postgresql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8",
                    DB_HOST, DB_PORT, name
            ));
            config.setUsername(TEACHER_USER);
            config.setPassword(TEACHER_PASSWORD);
            config.setMaximumPoolSize(3);
            config.setMinimumIdle(1);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setInitializationFailTimeout(30000);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName(String.format("teacher-pool-%s", name));
            config.addDataSourceProperty("stringtype", "unspecified");
            return new HikariDataSource(config);
        });
    }

    /**
     * Создаёт или возвращает существующий пул соединений для студентов.
     * Соединения открываются в режиме только для чтения.
     *
     * @throws RuntimeException если превышен лимит количества учебных баз данных
     */
    private static HikariDataSource getStudentDataSource(String dbName) {
        // Проверка лимита перед созданием нового пула
        if (!studentDataSources.containsKey(dbName) && studentDataSources.size() >= MAX_STUDENT_POOLS) {
            String error = String.format(
                    "Превышен лимит количества учебных баз данных (%d). Невозможно создать пул для '%s'",
                    MAX_STUDENT_POOLS, dbName);
            log.error(error);
            throw new RuntimeException(error);
        }

        return studentDataSources.computeIfAbsent(dbName, name -> {
            log.info("Creating student pool for database: {} (current pools: {}/{})",
                    name, studentDataSources.size() + 1, MAX_STUDENT_POOLS);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format(
                    "jdbc:postgresql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8",
                    DB_HOST, DB_PORT, name
            ));
            config.setUsername(STUDENT_USER);
            config.setPassword(STUDENT_PASSWORD);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setReadOnly(true);
            config.setInitializationFailTimeout(30000);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName(String.format("student-pool-%s", name));
            config.addDataSourceProperty("stringtype", "unspecified");
            config.addDataSourceProperty("connectTimeout", "5");
            config.addDataSourceProperty("socketTimeout", "10");
            return new HikariDataSource(config);
        });
    }

    /**
     * Устанавливает ограничения для сессии студента на уровне PostgreSQL.
     * Ограничивает время выполнения запроса, память и кодировку.
     */
    private static void applyStudentLimits(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("SET statement_timeout = '%ds'", QUERY_TIMEOUT_SEC));
            stmt.execute("SET work_mem = '4MB'");
            stmt.execute("SET idle_in_transaction_session_timeout = '30s'");
            stmt.execute("SET client_encoding = 'UTF8'");
            stmt.execute("SET NAMES 'UTF8'");
            stmt.execute("SET timezone = 'Europe/Moscow'");
        } catch (SQLException e) {
            log.warn("Could not set student session limits: {}", e.getMessage());
        }
    }

    /**
     * Закрывает пул соединений студентов для указанной базы.
     * Вызывается при удалении базы данных.
     */
    public static void closeStudentPool(String dbName) {
        HikariDataSource ds = studentDataSources.remove(dbName);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed student pool for database: {}", dbName);
        }
    }

    /**
     * Закрывает пул соединений преподавателя для указанной базы.
     * Вызывается при удалении базы данных.
     */
    public static void closeTeacherPool(String dbName) {
        HikariDataSource ds = teacherDataSources.remove(dbName);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed teacher pool for database: {}", dbName);
        }
    }

    /**
     * Закрывает все пулы соединений при остановке приложения.
     */
    public static void closeAllPools() {
        if (adminDataSource != null && !adminDataSource.isClosed()) {
            adminDataSource.close();
        }
        studentDataSources.forEach((name, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("Closed student pool for: {}", name);
            }
        });
        studentDataSources.clear();
        teacherDataSources.forEach((name, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("Closed teacher pool for: {}", name);
            }
        });
        teacherDataSources.clear();
        log.info("All database pools closed");
    }

    public static int getQueryTimeout() {
        return QUERY_TIMEOUT_SEC;
    }

    /**
     * Возвращает максимальное количество строк для указанной базы данных.
     * Значение берётся из таблицы databases_metadata (поле max_rows).
     * Если значение не задано или равно 0, возвращается 20 по умолчанию.
     *
     * @param dbName имя базы данных
     * @return максимальное количество строк, которое может вернуть SELECT-запрос студента
     */
    public static int getMaxRowsForDatabase(String dbName) {
        try (Connection conn = getConnection(Role.ADMIN, null);
             PreparedStatement stmt = conn.prepareStatement("SELECT max_rows FROM databases_metadata WHERE db_name = ?")) {
            stmt.setString(1, dbName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int maxRows = rs.getInt("max_rows");
                return maxRows > 0 ? maxRows : 20;
            }
        } catch (SQLException e) {
            log.warn("Failed to get max_rows for database {}, using default 20", dbName, e);
        }
        return 20;
    }

    /**
     * Возвращает текущее количество активных пулов студентов.
     */
    public static int getActiveStudentPoolsCount() {
        return studentDataSources.size();
    }

    /**
     * Возвращает текущее количество активных пулов преподавателей.
     */
    public static int getActiveTeacherPoolsCount() {
        return teacherDataSources.size();
    }

    /**
     * Возвращает максимально допустимое количество учебных баз данных.
     */
    public static int getMaxDatabasesLimit() {
        return MAX_DATABASES;
    }
}