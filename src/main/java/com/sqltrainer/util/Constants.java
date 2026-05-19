package com.sqltrainer.util;

/**
 * Класс для хранения констант приложения.
 * Все магические числа и строки вынесены сюда для централизованного управления.
 */
public final class Constants {

    private Constants() {
        // Приватный конструктор для предотвращения создания экземпляров
    }

    // ============================================
    // ОГРАНИЧЕНИЯ ЗАПРОСОВ
    // ============================================

    /** Максимальная длина SQL запроса в символах */
    public static final int MAX_QUERY_LENGTH = 10000;

    // ============================================
    // РАЗМЕРЫ И ЛИМИТЫ
    // ============================================

    /** Максимальный размер SQL скрипта для загрузки (2MB) */
    public static final int MAX_SCRIPT_SIZE_BYTES = 2 * 1024 * 1024;

    /** Максимальный размер файла для загрузки (10MB) */
    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    /** Максимальный размер запроса (15MB) */
    public static final int MAX_REQUEST_SIZE_BYTES = 15 * 1024 * 1024;

    /** Максимальная длина имени базы данных */
    public static final int MAX_DB_NAME_LENGTH = 63;

    // ============================================
    // ОЧЕРЕДЬ ЛОГОВ
    // ============================================

    /** Максимальный размер очереди логов */
    public static final int LOG_QUEUE_MAX_SIZE = 10000;

    /** Таймаут ожидания сообщения в логах (сек) */
    public static final int LOG_POLL_TIMEOUT_SEC = 30;

    /** Интервал очистки очереди логов (мс) */
    public static final long LOG_CLEANUP_INTERVAL_MS = 60000;

    // ============================================
    // БАЗЫ ДАННЫХ
    // ============================================

    /** Защищённые базы данных (нельзя удалить) - только системные PostgreSQL */
    public static final String[] PROTECTED_DATABASES = {
            "postgres",
            "template0",
            "template1"
    };

    /** Разрешённые расширения файлов для загрузки SQL */
    public static final String[] ALLOWED_SQL_EXTENSIONS = {"sql"};

    // ============================================
    // ОПАСНЫЕ SQL ПАТТЕРНЫ
    // ============================================

    /** Опасные SQL команды для блокировки */
    public static final String[] DANGEROUS_SQL_PATTERNS = {
            "delete", "update", "insert", "drop", "truncate", "alter",
            "create", "grant", "revoke", "pg_sleep", "benchmark"
    };

    /** Опасные команды в SQL скриптах для загрузки */
    public static final String[] DANGEROUS_SCRIPT_PATTERNS = {
            "drop database", "drop table", "delete from", "truncate",
            "alter system", "pg_sleep", "copy"
    };

    // ============================================
    // ЗАГОЛОВКИ БЕЗОПАСНОСТИ
    // ============================================

    /** Content-Security-Policy заголовок */
    public static final String CSP_POLICY =
            "default-src 'self'; " +
                    "script-src 'self' https://cdnjs.cloudflare.com 'unsafe-inline' 'unsafe-eval'; " +
                    "style-src 'self' https://cdnjs.cloudflare.com 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'";

    // ============================================
    // ЗАРЕЗЕРВИРОВАННЫЕ СЛОВА
    // ============================================

    /** Зарезервированные SQL слова для валидации идентификаторов */
    public static final String[] RESERVED_SQL_WORDS = {
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
            "ALTER", "TRUNCATE", "GRANT", "REVOKE", "UNION", "WHERE"
    };

    /** Системные базы данных PostgreSQL */
    public static final String[] SYSTEM_DATABASES = {
            "postgres", "template0", "template1"
    };
}