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

    /** Минимальный интервал между запросами от одного пользователя (мс) */
    public static final long MIN_REQUEST_INTERVAL_MS = 1000;

    // ============================================
    // УПРАВЛЕНИЕ СЕССИЯМИ
    // ============================================

    /** Время жизни сессии студента (мс) - 30 минут */
    public static final long SESSION_TTL_MS = 30 * 60 * 1000;

    /** Время жизни записи rate limiting (мс) - 1 минута */
    public static final long RATE_LIMIT_TTL_MS = 60 * 1000;

    // ============================================
    // РАЗМЕРЫ И ЛИМИТЫ
    // ============================================

    /** Максимальный размер SQL скрипта для загрузки (2MB) */
    public static final int MAX_SCRIPT_SIZE_BYTES = 2 * 1024 * 1024;

    /** Максимальный размер файла для загрузки (10MB) */
    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    /** Максимальный размер запроса (15MB) */
    public static final int MAX_REQUEST_SIZE_BYTES = 15 * 1024 * 1024;

    /** Максимальное количество студентов за одну генерацию */
    public static final int MAX_STUDENTS_PER_GENERATION = 100;

    /** Длина генерируемого пароля */
    public static final int GENERATED_PASSWORD_LENGTH = 10;

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

    /** Защищённые базы данных (нельзя удалить) */
    public static final String[] PROTECTED_DATABASES = {
            "postgres",
            "template0",
            "template1"
    };

    /** Разрешённые для студентов базы данных */
    public static final String[] ALLOWED_STUDENT_DATABASES = {
            "sql_tutor_university_db",
            "archaeology_10m"
    };

    /** Разрешённые расширения файлов для загрузки SQL */
    public static final String[] ALLOWED_SQL_EXTENSIONS = {"sql"};

    // ============================================
    // ПАРОЛИ
    // ============================================

    /** Набор символов для генерации паролей (заглавные) */
    public static final String PASSWORD_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** Набор символов для генерации паролей (строчные) */
    public static final String PASSWORD_LOWERCASE = "abcdefghijkmnpqrstuvwxyz";

    /** Набор символов для генерации паролей (цифры) */
    public static final String PASSWORD_DIGITS = "23456789";

    /** Набор символов для генерации паролей (спецсимволы) */
    public static final String PASSWORD_SPECIAL = "!@#$%^&*";

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