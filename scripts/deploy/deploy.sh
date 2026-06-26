#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================"
echo "   SQL Trainer - Развертывание"
echo "========================================"
echo ""

# Переходим в корень проекта
cd "$(dirname "$0")/../.."

# Проверка, что мы в правильной директории
if [ ! -f pom.xml ]; then
    echo -e "${RED}[ERROR] Не найден pom.xml. Убедитесь, что скрипт находится в папке scripts/deploy${NC}"
    exit 1
fi

echo -e "${GREEN}[OK] Корень проекта: $(pwd)${NC}"
echo ""

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo -e "${YELLOW}[WARN] Файл .env не найден!${NC}"
    echo "Создаю файл .env с настройками по умолчанию..."

    cat > .env << 'EOF'
# Database configuration
DB_HOST=postgres
DB_PORT=5432
DB_ADMIN_USER=postgres
DB_ADMIN_PASSWORD=postgres
DB_TEACHER_USER=teacher_role
DB_TEACHER_PASSWORD=teacher_pass
DB_STUDENT_USER=students
DB_STUDENT_PASSWORD=student_pass

# Limits
QUERY_TIMEOUT_SEC=30
MAX_ROWS=1000
CONNECTION_TIMEOUT_MS=5000

# Concurrency limits
MAX_CONCURRENT_QUERIES=10
SEMAPHORE_TIMEOUT_SEC=30

# Logging
LOG_LEVEL=INFO
EOF
    echo -e "${GREEN}[OK] Файл .env создан${NC}"
    echo ""
else
    echo -e "${GREEN}[OK] Файл .env найден${NC}"
    echo ""
fi

# Сборка проекта
echo "[1/5] Сборка проекта..."
mvn clean package

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR] Ошибка сборки проекта!${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Сборка завершена успешно${NC}"
echo ""

# Переходим в папку с Docker файлами
cd docker

# Копируем .env из корня в папку docker (если есть)
if [ -f ../.env ]; then
    cp ../.env .env
    echo -e "${GREEN}[OK] .env скопирован в папку docker${NC}"
else
    echo -e "${YELLOW}[WARN] .env не найден в корне${NC}"
fi

# Проверка наличия docker-compose.yml
if [ ! -f docker-compose.yml ]; then
    echo -e "${RED}[ERROR] Файл docker-compose.yml не найден в папке docker${NC}"
    exit 1
fi

# Спрашиваем про удаление данных
echo "[2/5] Подготовка контейнеров..."
echo ""
read -p "Удалить ВСЕ данные БД и начать с чистого листа? (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}[INFO] Удаление данных БД...${NC}"
    docker-compose down -v 2>/dev/null
    docker volume rm sql_trainer_postgres_data sql_trainer_uploads 2>/dev/null
    echo -e "${GREEN}[OK] Контейнеры остановлены, данные удалены${NC}"
    REMOVE_DATA=true
else
    echo -e "${BLUE}[INFO] Сохранение данных БД...${NC}"
    docker-compose down 2>/dev/null
    echo -e "${GREEN}[OK] Контейнеры остановлены, данные сохранены${NC}"
    REMOVE_DATA=false
fi
echo ""

# Запуск новых контейнеров
echo "[3/5] Запуск контейнеров..."
docker-compose up -d --build

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR] Ошибка при запуске контейнеров!${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Контейнеры запущены${NC}"
echo ""

# Ожидание готовности PostgreSQL
echo "[4/5] Ожидание готовности PostgreSQL..."
echo -e "${YELLOW}[INFO] Ожидание 30 секунд для инициализации PostgreSQL...${NC}"
sleep 30

# Выполнение скрипта настройки метаданных
echo "[5/5] Настройка метаданных баз данных..."
docker exec -i sql_trainer_postgres psql -U postgres < ../scripts/db/setup_metadata.sql 2>/dev/null
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}[WARN] Не удалось выполнить setup_metadata.sql${NC}"
else
    echo -e "${GREEN}[OK] Таблицы метаданных созданы${NC}"
fi

echo ""
docker-compose ps

cd ..

echo ""
echo "========================================"
echo "   РАЗВЕРТЫВАНИЕ ЗАВЕРШЕНО"
echo "========================================"
echo ""
echo -e "${GREEN}[i] Приложение: http://localhost:8081${NC}"
echo -e "${GREEN}[i] Страница тренажёра: http://localhost:8081/index${NC}"
echo -e "${GREEN}[i] Панель преподавателя: http://localhost:8081/teacher${NC}"
echo -e "${GREEN}[i] Пароль преподавателя: teacher123${NC}"
echo ""