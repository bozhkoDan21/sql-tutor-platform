#!/bin/bash

echo "🚀 Начало развертывания SQL Tutor..."

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo "Файл .env не найден. Создаю из шаблона..."
    echo "TEACHER_SECRET=teacher123" > .env
    echo "DB_ADMIN_PASSWORD=postgres" >> .env
    echo "Файл .env создан. Рекомендуется изменить пароли!"
fi

# Сборка проекта
echo "Сборка проекта..."
mvn clean package

if [ $? -ne 0 ]; then
    echo "❌ Ошибка сборки проекта!"
    exit 1
fi

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

echo "Развертывание завершено!"
echo "Приложение доступно по адресу: http://localhost:8081"
echo "Вход для преподавателя: http://localhost:8081/teacher-login.jsp"
echo "   Пароль: $(grep TEACHER_SECRET .env | cut -d'=' -f2)"