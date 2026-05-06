@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    SQL Trainer - Развертывание
echo ========================================
echo.

REM Переходим в корень проекта
cd /d "%~dp0../.."

REM Проверка наличия pom.xml
if not exist pom.xml (
    echo [ERROR] Не найден pom.xml в текущей папке
    pause
    exit /b 1
)

echo [OK] Корень проекта: %CD%
echo.

REM Проверка наличия .env файла
if not exist .env (
    echo [WARN] Файл .env не найден!
    echo Создаю файл .env с настройками по умолчанию...
    (
        echo # Database configuration
        echo DB_HOST=postgres
        echo DB_PORT=5432
        echo DB_ADMIN_USER=postgres
        echo DB_ADMIN_PASSWORD=postgres
        echo DB_TEACHER_USER=teacher_role
        echo DB_TEACHER_PASSWORD=teacher_pass
        echo DB_STUDENT_USER=students
        echo DB_STUDENT_PASSWORD=student_pass
        echo.
        echo # Teacher authentication
        echo TEACHER_PASSWORD=teacher123
        echo.
        echo # Limits
        echo QUERY_TIMEOUT_SEC=3
        echo MAX_ROWS=1000
        echo CONNECTION_TIMEOUT_MS=5000
        echo.
        echo # Concurrency limits
        echo MAX_CONCURRENT_QUERIES=10
        echo SEMAPHORE_TIMEOUT_SEC=30
        echo.
        echo # Logging
        echo LOG_LEVEL=INFO
    ) > .env
    echo [OK] Файл .env создан
    echo.
) else (
    echo [OK] Файл .env найден
    echo.
)

REM Сборка проекта
echo [1/5] Сборка проекта...
call mvn clean package

if %errorlevel% neq 0 (
    echo [ERROR] Ошибка сборки проекта!
    pause
    exit /b %errorlevel%
)
echo [OK] Сборка завершена успешно
echo.

REM Переходим в папку с Docker файлами
cd docker

REM Копируем .env из корня в папку docker (если есть)
if exist ..\.env (
    copy ..\.env .env >nul
    echo [OK] .env скопирован в папку docker
) else (
    echo [WARN] .env не найден в корне
)

REM Проверка наличия docker-compose.yml
if not exist docker-compose.yml (
    echo [ERROR] Файл docker-compose.yml не найден в папке docker
    pause
    exit /b 1
)

REM Спрашиваем про удаление данных
echo [2/5] Подготовка контейнеров...
echo.
echo ВНИМАНИЕ! Генерация учебных баз данных может занять 30-40 минут.
echo.
set REMOVE_DATA=N
set /p REMOVE_DATA="Удалить ВСЕ данные БД и пересоздать учебные базы? (Y/N - по умолчанию N): "
if /i "!REMOVE_DATA!"=="Y" (
    echo [INFO] Удаление данных БД...
    docker-compose down -v 2>nul
    echo [OK] Контейнеры остановлены, данные удалены
    set NEED_SETUP=Y
) else (
    echo [INFO] Сохранение данных БД...
    docker-compose down 2>nul
    echo [OK] Контейнеры остановлены, данные сохранены
    set NEED_SETUP=N
)
echo.

REM Запуск новых контейнеров
echo [3/5] Запуск контейнеров...
docker-compose up -d --build

if %errorlevel% neq 0 (
    echo [ERROR] Ошибка при запуске контейнеров!
    pause
    exit /b %errorlevel%
)
echo [OK] Контейнеры запущены
echo.

REM Ожидание готовности PostgreSQL
echo [4/5] Ожидание готовности PostgreSQL...
if /i "!REMOVE_DATA!"=="Y" (
    echo [INFO] PostgreSQL пересоздаётся, ждём 10 секунд...
    timeout /t 10 /nobreak >nul
) else (
    timeout /t 5 /nobreak >nul
)

REM Выполнение скрипта настройки метаданных (папки, права доступа, сессии)
echo [5/5] Настройка метаданных баз данных...
docker exec -i sql_trainer_postgres psql -U postgres < ..\scripts\db\setup_metadata.sql 2>nul
if !errorlevel! neq 0 (
    echo [WARN] Не удалось выполнить setup_metadata.sql
) else (
    echo [OK] Таблицы метаданных созданы
)

REM Выполнение скрипта учебных баз (только если данные были удалены)
if /i "!REMOVE_DATA!"=="Y" (
    echo.
    echo [INFO] Запуск генерации учебных баз данных (может занять 30-40 минут)...
    echo [INFO] Следите за логами PostgreSQL: docker logs -f sql_trainer_postgres
    echo.
    docker exec -i sql_trainer_postgres psql -U postgres < ..\scripts\db\setup_database.sql 2>nul
    if !errorlevel! neq 0 (
        echo [WARN] Не удалось выполнить setup_database.sql
    ) else (
        echo [OK] Учебные базы данных созданы
    )
) else (
    echo [SKIP] Учебные базы данных не пересозданы (данные сохранены)
)

echo.
docker-compose ps

cd ..

echo.
echo ========================================
echo    РАЗВЕРТЫВАНИЕ ЗАВЕРШЕНО
echo ========================================
echo.
echo [i] Приложение: http://localhost:8081
echo [i] Страница тренажёра: http://localhost:8081/index
echo [i] Панель преподавателя: http://localhost:8081/teacher
echo [i] Пароль преподавателя: teacher123
echo.

pause