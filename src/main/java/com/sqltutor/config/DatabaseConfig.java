package com.sqltutor.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер подключений к базе данных с пулом соединений.
 * <p>
 * Обеспечивает изолированные подключения для разных ролей (администратор, преподаватель, студент)
 * с использованием HikariCP для эффективного управления соединениями.
 * </p>
 *
 * <p>Особенности:
 * <ul>
 *   <li>Для студентов: только чтение, ограничения по времени и памяти</li>
 *   <li>Для преподавателей: полные права на свои базы данных</li>
 *   <li>Для администратора: создание и удаление баз данных</li>
 * </ul>
 * </p>
 */
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    /** Пул соединений для администратора (создание/удаление БД) */
    private static final HikariDataSource adminDataSource;

    /** Пулы для студентов: ключ = имя базы данных */
    private static final Map<String, HikariDataSource> studentDataSources = new ConcurrentHashMap<>();

    /** Пулы для преподавателей: ключ = имя базы данных */
    private static final Map<String, HikariDataSource> teacherDataSources = new ConcurrentHashMap<>();

    // Настройки из переменных окружения со значениями по умолчанию
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String ADMIN_USER = System.getenv().getOrDefault("DB_ADMIN_USER", "postgres");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("DB_ADMIN_PASSWORD", "postgres");
    private static final String TEACHER_USER = System.getenv().getOrDefault("DB_TEACHER_USER", "teacher_role");
    private static final String TEACHER_PASSWORD = System.getenv().getOrDefault("DB_TEACHER_PASSWORD", "teacher_pass");
    private static final String STUDENT_USER = System.getenv().getOrDefault("DB_STUDENT_USER", "students");
    private static final String STUDENT_PASSWORD = System.getenv().getOrDefault("DB_STUDENT_PASSWORD", "student_pass");

    /** Тайм-аут выполнения запроса для студентов (секунды) */
    private static final int QUERY_TIMEOUT_SEC = Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT_SEC", "3"));

    /** Максимальное количество строк в результате для студентов */
    private static final int MAX_ROWS = Integer.parseInt(System.getenv().getOrDefault("MAX_ROWS", "1000"));

    /** Тайм-аут подключения к БД (миллисекунды) */
    private static final int CONNECTION_TIMEOUT_MS = Integer.parseInt(System.getenv().getOrDefault("CONNECTION_TIMEOUT_MS", "5000"));

    static {
        // ЯВНАЯ ЗАГРУЗКА ДРАЙВЕРА POSTGRESQL
        try {
            Class.forName("org.postgresql.Driver");
            log.info("PostgreSQL JDBC Driver loaded successfully");
        } catch (ClassNotFoundException e) {
            log.error("Failed to load PostgreSQL JDBC Driver", e);
            throw new RuntimeException("PostgreSQL JDBC Driver not found", e);
        }

        // Инициализация пула для администратора
        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/postgres", DB_HOST, DB_PORT));
        adminConfig.setUsername(ADMIN_USER);
        adminConfig.setPassword(ADMIN_PASSWORD);
        adminConfig.setMaximumPoolSize(2);
        adminConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        adminConfig.setInitializationFailTimeout(30000);
        adminConfig.setConnectionTestQuery("SELECT 1");
        adminConfig.setPoolName("admin-pool");

        log.info("Creating admin pool with URL: jdbc:postgresql://{}:{}/postgres", DB_HOST, DB_PORT);

        adminDataSource = new HikariDataSource(adminConfig);

        log.info("DatabaseConfig initialized with PostgreSQL at {}:{}", DB_HOST, DB_PORT);
    }

    /**
     * Роли пользователей системы.
     * <ul>
     *   <li>ADMIN — создание и удаление баз данных (используется при загрузке скриптов)</li>
     *   <li>TEACHER — управление своей базой (схема, права доступа)</li>
     *   <li>STUDENT — только чтение (SELECT) с ограничениями</li>
     * </ul>
     */
    public enum Role {
        ADMIN, TEACHER, STUDENT
    }

    /**
     * Получает соединение с базой данных для указанной роли.
     *
     * @param role   роль пользователя (ADMIN, TEACHER, STUDENT)
     * @param dbName имя базы данных (обязательно для STUDENT, опционально для других)
     * @return активное соединение с БД
     * @throws SQLException если не удалось установить соединение или роль требует dbName
     */
    public static Connection getConnection(Role role, String dbName) throws SQLException {
        if (role == Role.STUDENT && (dbName == null || dbName.isEmpty())) {
            throw new SQLException("Database name is required for STUDENT connection");
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
     * Возвращает пул соединений для преподавателя к указанной базе данных.
     * Пул создаётся лениво при первом обращении и кешируется.
     *
     * @param dbName имя базы данных
     * @return пул соединений HikariCP для преподавателя
     */
    private static HikariDataSource getTeacherDataSource(String dbName) {
        return teacherDataSources.computeIfAbsent(dbName, name -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, name));
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
            return new HikariDataSource(config);
        });
    }

    /**
     * Возвращает пул соединений для студентов к указанной базе данных.
     * Соединения открываются в режиме только для чтения (readOnly=true).
     * <p>
     * Настройки пула:
     * <ul>
     *   <li>maximumPoolSize=5 — максимум 5 одновременных соединений на базу</li>
     *   <li>minimumIdle=1 — всегда держим 1 готовое соединение</li>
     *   <li>idleTimeout=300000 — закрываем неиспользуемые соединения через 5 минут</li>
     *   <li>maxLifetime=600000 — пересоздаём соединения каждые 10 минут</li>
     * </ul>
     * </p>
     *
     * @param dbName имя базы данных
     * @return пул соединений HikariCP для студентов
     */
    private static HikariDataSource getStudentDataSource(String dbName) {
        return studentDataSources.computeIfAbsent(dbName, name -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, name));
            config.setUsername(STUDENT_USER);
            config.setPassword(STUDENT_PASSWORD);
            config.setMaximumPoolSize(5);           // Уменьшено с 10 до 5
            config.setMinimumIdle(1);               // Держим 1 готовое соединение
            config.setIdleTimeout(300000);           // 5 минут простоя — закрыть лишние
            config.setMaxLifetime(600000);           // 10 минут жизни соединения
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setReadOnly(true);
            config.setInitializationFailTimeout(30000);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName(String.format("student-pool-%s", name));

            // Тайм-ауты на уровне драйвера
            config.addDataSourceProperty("connectTimeout", "5");
            config.addDataSourceProperty("socketTimeout", "10");

            return new HikariDataSource(config);
        });
    }

    /**
     * Применяет ограничения к студенческой сессии на уровне PostgreSQL.
     * <ul>
     *   <li>statement_timeout — максимальное время выполнения запроса</li>
     *   <li>work_mem — ограничение памяти для сортировки и хеш-таблиц</li>
     *   <li>idle_in_transaction_session_timeout — автоматическое закрытие зависших сессий</li>
     * </ul>
     *
     * @param conn соединение с БД
     */
    private static void applyStudentLimits(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("SET statement_timeout = '%ds'", QUERY_TIMEOUT_SEC));
            stmt.execute("SET work_mem = '4MB'");
            stmt.execute("SET idle_in_transaction_session_timeout = '30s'");
        } catch (SQLException e) {
            log.warn("Could not set student session limits: {}", e.getMessage());
        }
    }

    /**
     * Закрывает пул соединений для указанной базы данных (студенческий).
     * Используется при удалении базы данных.
     *
     * @param dbName имя базы данных
     */
    public static void closeStudentPool(String dbName) {
        HikariDataSource ds = studentDataSources.remove(dbName);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed student pool for database: {}", dbName);
        }
    }

    /**
     * Закрывает пул соединений для указанной базы данных (преподавательский).
     * Используется при удалении базы данных.
     *
     * @param dbName имя базы данных
     */
    public static void closeTeacherPool(String dbName) {
        HikariDataSource ds = teacherDataSources.remove(dbName);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed teacher pool for database: {}", dbName);
        }
    }

    /**
     * Закрывает все пулы соединений.
     * Должен вызываться при остановке приложения (например, в ServletContextListener).
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

    /**
     * Возвращает тайм-аут выполнения запроса для студентов.
     *
     * @return тайм-аут в секундах
     */
    public static int getQueryTimeout() {
        return QUERY_TIMEOUT_SEC;
    }

    /**
     * Возвращает максимальное количество строк в результате для студентов.
     *
     * @return максимальное количество строк
     */
    public static int getMaxRows() {
        return MAX_ROWS;
    }

    /**
     * Возвращает секретный ключ для входа в панель преподавателя.
     *
     * @return секретный ключ из переменной окружения или значение по умолчанию
     */
    public static String getTeacherSecret() {
        return System.getenv().getOrDefault("TEACHER_SECRET", "teacher123");
    }
}