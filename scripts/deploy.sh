#!/bin/bash

# Переходим в корень проекта
cd "$(dirname "$0")/.."

echo "🚀 Начало развертывания SQL Trainer..."

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo "Файл .env не найден. Создаю из config/.env.example..."
    if [ -f config/.env.example ]; then
        cp config/.env.example .env
        echo "Файл .env создан из шаблона"
        echo "Рекомендуется изменить пароль TEACHER_SECRET в файле .env"
    else
        echo "Файл config/.env.example не найден!"
        echo "Создаю базовый .env..."
        echo "TEACHER_SECRET=teacher123" > .env
        echo "DB_ADMIN_PASSWORD=postgres" >> .env
    fi
fi

# Сборка проекта
echo "Сборка проекта..."
mvn clean package

if [ $? -ne 0 ]; then
    echo "Ошибка сборки проекта!"
    exit 1
fi

# Переходим в папку с Docker файлами
cd docker

# Остановка старых контейнеров
echo "Остановка старых контейнеров..."
docker-compose down

# Запуск новых контейнеров
echo "Запуск контейнеров..."
docker-compose up -d --build

# Проверка статуса
echo "Проверка статуса контейнеров..."
sleep 5
docker-compose ps

cd ..

echo "Развертывание завершено!"
echo "Приложение доступно по адресу: http://localhost:8081"
echo "Вход для преподавателя: http://localhost:8081/teacher-login.jsp"
echo "   Пароль: $(grep TEACHER_SECRET .env | cut -d'=' -f2)"