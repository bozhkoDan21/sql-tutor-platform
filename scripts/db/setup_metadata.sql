-- ============================================
-- СКРИПТ НАСТРОЙКИ МЕТАДАННЫХ БАЗ ДАННЫХ
-- ============================================

-- Устанавливаем кодировку
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- ============================================
-- СОЗДАНИЕ РОЛЕЙ
-- ============================================

DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'teacher_role') THEN
            CREATE ROLE teacher_role WITH LOGIN PASSWORD 'teacher_pass' CREATEDB;
            RAISE NOTICE 'Роль teacher_role создана';
        ELSE
            RAISE NOTICE 'Роль teacher_role уже существует';
        END IF;

        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'students') THEN
            CREATE ROLE students WITH LOGIN PASSWORD 'student_pass';
            RAISE NOTICE 'Роль students создана';
        ELSE
            RAISE NOTICE 'Роль students уже существует';
        END IF;
    END
$$;

-- Удаляем старые таблицы (если есть)
DROP TABLE IF EXISTS active_sessions CASCADE;
DROP TABLE IF EXISTS databases_metadata CASCADE;
DROP TABLE IF EXISTS database_folders CASCADE;

-- ============================================
-- ТАБЛИЦА ПАПОК (КАТЕГОРИЙ) ДЛЯ БАЗ ДАННЫХ
-- ============================================

CREATE TABLE database_folders (
                                  id SERIAL PRIMARY KEY,
                                  name VARCHAR(255) NOT NULL,
                                  owner_id VARCHAR(100) NOT NULL,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ТАБЛИЦА МЕТАДАННЫХ БАЗ ДАННЫХ
-- ============================================

CREATE TABLE databases_metadata (
                                    db_name VARCHAR(63) PRIMARY KEY,
                                    folder_id INTEGER NOT NULL REFERENCES database_folders(id) ON DELETE CASCADE,
                                    display_name VARCHAR(255) NOT NULL,
                                    access_password_hash VARCHAR(255),
                                    is_visible BOOLEAN DEFAULT TRUE,
                                    access_start DATE,
                                    access_end DATE,
                                    schema_image_url VARCHAR(500),
                                    created_by VARCHAR(100) NOT NULL,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ТАБЛИЦА АКТИВНЫХ СЕССИЙ СТУДЕНТОВ
-- ============================================

CREATE TABLE active_sessions (
                                 session_id VARCHAR(255) PRIMARY KEY,
                                 db_name VARCHAR(63) NOT NULL,
                                 ip_address INET,
                                 last_query TEXT,
                                 last_query_time_ms BIGINT,
                                 last_access TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 is_blocked BOOLEAN DEFAULT FALSE,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ИНДЕКСЫ ДЛЯ ОПТИМИЗАЦИИ
-- ============================================

CREATE INDEX idx_folders_owner ON database_folders(owner_id);
CREATE INDEX idx_metadata_folder ON databases_metadata(folder_id);
CREATE INDEX idx_metadata_visibility ON databases_metadata(is_visible);
CREATE INDEX idx_metadata_dates ON databases_metadata(access_start, access_end);
CREATE INDEX idx_metadata_owner ON databases_metadata(created_by);
CREATE INDEX idx_sessions_db ON active_sessions(db_name);
CREATE INDEX idx_sessions_blocked ON active_sessions(is_blocked);
CREATE INDEX idx_sessions_last_access ON active_sessions(last_access);

-- ============================================
-- ФУНКЦИИ ДЛЯ АВТОМАТИЧЕСКОГО ОБНОВЛЕНИЯ updated_at
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_folder_updated_at
    BEFORE UPDATE ON database_folders
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_metadata_updated_at
    BEFORE UPDATE ON databases_metadata
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- ПРАВА ДОСТУПА ДЛЯ ПРЕПОДАВАТЕЛЯ
-- ============================================

-- Даём права на управление папками и метаданными
GRANT SELECT, INSERT, UPDATE, DELETE ON database_folders TO teacher_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON databases_metadata TO teacher_role;

-- Даём права на последовательности (ПОСЛЕ создания таблиц)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO teacher_role;

-- ============================================
-- ПРОВЕРКА
-- ============================================

SELECT '✅ Таблицы метаданных баз данных созданы' as message;
SELECT '✅ Роли и права для teacher_role настроены' as message;