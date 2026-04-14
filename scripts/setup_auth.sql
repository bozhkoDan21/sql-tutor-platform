-- ============================================
-- СКРИПТ НАСТРОЙКИ АВТОРИЗАЦИИ (Единая таблица users)
-- ============================================

-- Удаляем старые таблицы (если есть)
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================
-- ТАБЛИЦА ПОЛЬЗОВАТЕЛЕЙ (единая)
-- ============================================

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    login VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'student', -- 'student' or 'teacher'
    group_name VARCHAR(100),
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ТАБЛИЦА REFRESH TOKEN'ОВ
-- ============================================

CREATE TABLE refresh_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ТАБЛИЦА ЛОГОВ ДЕЙСТВИЙ
-- ============================================

CREATE TABLE action_logs (
    id SERIAL PRIMARY KEY,
    user_id INTEGER,
    action VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ВСТАВКА ПРЕПОДАВАТЕЛЯ ПО УМОЛЧАНИЮ
-- Пароль: teacher123
-- Хеш сгенерирован Java BCrypt (cost factor 10)
-- ============================================

INSERT INTO users (login, password_hash, full_name, email, role)
VALUES ('teacher', '$2a$10$L/3Cg8KvdHuq5Ti4t0ZrzOYylUp0G6WpCcLmhqc0Ea.lAU3GJITOO', 'Преподаватель', 'teacher@sqltrainer.com', 'teacher')
ON CONFLICT (login) DO NOTHING;

-- ============================================
-- ИНДЕКСЫ
-- ============================================

CREATE INDEX idx_users_login ON users(login);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- ============================================
-- ФУНКЦИЯ ДЛЯ ОБНОВЛЕНИЯ ВРЕМЕНИ ПОСЛЕДНЕЙ АКТИВНОСТИ
-- ============================================

CREATE OR REPLACE FUNCTION update_last_activity()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.last_activity_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_user_activity
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE FUNCTION update_last_activity();

-- ============================================
-- ПРОВЕРКА
-- ============================================

SELECT '✅ Таблицы авторизации созданы' as message;