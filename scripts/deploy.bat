@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo 🚀 Начало развертывания SQL Tutor...
echo.

REM Проверка наличия .env файла
if not exist .env (
    echo ⚠️ Файл .env не найден. Создаю из config\.env.example...
    if exist config\.env.example (
        copy config\.env.example .env
        echo ✅ Файл .env создан из шаблона
        echo ℹ️ Рекомендуется изменить пароль TEACHER_SECRET в файле .env
    ) else (
        echo ⚠️ Файл config\.env.example не найден!
        echo TEACHER_SECRET=teacher123 > .env
        echo DB_ADMIN_PASSWORD=postgres >> .env
        echo ✅ Базовый .env создан
    )
    echo.
) else (
    echo ✅ Файл .env найден
    echo.
)

REM Сборка проекта
echo 📦 Сборка проекта...
call mvn clean package

if %errorlevel% neq 0 (
    echo ❌ Ошибка сборки проекта!
    pause
    exit /b %errorlevel%
)

echo ✅ Сборка завершена успешно
echo.

REM Остановка старых контейнеров
echo 🛑 Остановка старых контейнеров...
docker-compose down

if %errorlevel% neq 0 (
    echo ⚠️ Ошибка при остановке контейнеров, продолжаем...
)

echo.

REM Запуск новых контейнеров
echo 🐳 Запуск контейнеров...
docker-compose up -d --build

if %errorlevel% neq 0 (
    echo ❌ Ошибка при запуске контейнеров!
    pause
    exit /b %errorlevel%
)

echo.

REM Проверка статуса
echo 📊 Проверка статуса контейнеров...
timeout /t 5 /nobreak >nul
docker-compose ps

echo.
echo ✅ Развертывание завершено!
echo.
echo 🌐 Приложение доступно по адресу: http://localhost:8081
echo 🔐 Вход для преподавателя: http://localhost:8081/teacher-login.jsp

REM Чтение пароля из .env файла
for /f "tokens=2 delims==" %%a in ('findstr /i "TEACHER_SECRET" .env') do set pass=%%a
echo    Пароль: %pass%

echo.
pause