#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "   SQL Trainer - Развертывание"
echo "========================================"
echo ""

# Переходим в корень проекта
cd "$(dirname "$0")/.."

# Проверка, что мы в правильной директории
if [ ! -f pom.xml ]; then
    echo -e "${RED}[ERROR] Не найден pom.xml. Убедитесь, что скрипт находится в папке scripts${NC}"
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

# Security
TEACHER_SECRET=teacher123

# Limits
QUERY_TIMEOUT_SEC=3
MAX_ROWS=1000
CONNECTION_TIMEOUT_MS=5000
EOF
    echo -e "${GREEN}[OK] Файл .env создан${NC}"
    echo -e "${YELLOW}[i] Рекомендуется изменить TEACHER_SECRET в файле .env${NC}"
    echo ""
else
    echo -e "${GREEN}[OK] Файл .env найден${NC}"
    echo ""
fi

# Сборка проекта
echo "[1/4] Сборка проекта..."
mvn clean package

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR] Ошибка сборки проекта!${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Сборка завершена успешно${NC}"
echo ""

# Переходим в папку с Docker файлами
cd docker

# Проверка наличия docker-compose.yml
if [ ! -f docker-compose.yml ]; then
    echo -e "${RED}[ERROR] Файл docker-compose.yml не найден в папке docker${NC}"
    exit 1
fi

# Остановка старых контейнеров
echo "[2/4] Остановка старых контейнеров..."
docker-compose down 2>/dev/null
echo -e "${GREEN}[OK] Контейнеры остановлены${NC}"
echo ""

# Запуск новых контейнеров
echo "[3/4] Запуск контейнеров..."
docker-compose up -d --build

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR] Ошибка при запуске контейнеров!${NC}"
    exit 1
fi
echo -e "${GREEN}[OK] Контейнеры запущены${NC}"
echo ""

# Проверка статуса
echo "[4/4] Проверка статуса контейнеров..."
sleep 5
docker-compose ps

cd ..

echo ""
echo "========================================"
echo "   РАЗВЕРТЫВАНИЕ ЗАВЕРШЕНО"
echo "========================================"
echo ""
echo -e "${GREEN}[i] Приложение: http://localhost:8081${NC}"
echo -e "${GREEN}[i] Панель преподавателя: http://localhost:8081/teacher-login.jsp${NC}"

# Чтение пароля из .env файла
PASSWORD=$(grep TEACHER_SECRET .env | cut -d'=' -f2)
echo -e "${GREEN}[i] Пароль преподавателя: ${PASSWORD}${NC}"
echo ""