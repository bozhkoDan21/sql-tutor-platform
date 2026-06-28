@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ================================================================
:: УПРАВЛЕНИЕ ПРЕПОДАВАТЕЛЯМИ (Windows Batch)
:: ================================================================
:: Запуск: manage_teachers.bat
:: ================================================================

cd /d "%~dp0../.."

:: Проверяем, что проект собран
if not exist "target\classes\com\sqltrainer\util\PasswordHashGenerator.class" (
    echo.
    echo ВНИМАНИЕ: Проект не собран!
    echo Выполните: mvn clean package
    echo.
    pause
    goto menu
)

:menu
cls
echo ========================================
echo    УПРАВЛЕНИЕ ПРЕПОДАВАТЕЛЯМИ
echo ========================================
echo.
echo  1. Просмотреть список преподавателей
echo  2. Добавить преподавателя
echo  3. Сбросить пароль
echo  4. Удалить преподавателя
echo  5. Выход
echo.
set /p choice="Выберите действие (1-5): "

if "%choice%"=="1" goto show_teachers
if "%choice%"=="2" goto add_teacher
if "%choice%"=="3" goto change_password
if "%choice%"=="4" goto delete_teacher
if "%choice%"=="5" goto exit
echo Неверный выбор!
pause
goto menu

:show_teachers
cls
echo ========================================
echo    СПИСОК ПРЕПОДАВАТЕЛЕЙ
echo ========================================
echo.
docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО, created_at AS Создан FROM teacher_settings ORDER BY username;" 2>nul
echo.
pause
goto menu

:add_teacher
cls
echo ========================================
echo    ДОБАВЛЕНИЕ ПРЕПОДАВАТЕЛЯ
echo ========================================
echo.
set /p username="Введите логин преподавателя: "
if "%username%"=="" (
    echo ОШИБКА: Логин не может быть пустым!
    pause
    goto menu
)

:: Проверяем, существует ли преподаватель
docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT username FROM teacher_settings WHERE username = '%username%';" 2>nul | findstr /i /c:"%username%" >nul
if %errorlevel%==0 (
    echo ОШИБКА: Преподаватель '%username%' УЖЕ СУЩЕСТВУЕТ!
    pause
    goto menu
)

set /p fullname="Введите ФИО преподавателя: "
if "%fullname%"=="" set fullname=%username%

:: Генерируем случайный пароль
set "chars=abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
set "password="
for /l %%i in (1,1,8) do (
    set /a "idx=!random! %% 62"
    for %%j in (!idx!) do set "password=!password!!chars:~%%j,1!"
)

echo.
echo Сгенерирован пароль: %password%

:: Генерируем хеш через Java (новый класс выводит только хеш)
set "hash="
for /f "delims=" %%i in ('java -cp "target\classes;target\SQLTrainer\WEB-INF\lib\jbcrypt-0.4.jar" com.sqltrainer.util.PasswordHashGenerator %password% 2^>nul') do set "hash=%%i"

if "%hash%"=="" (
    echo.
    echo ОШИБКА: Не удалось сгенерировать хеш!
    echo Убедитесь, что проект собран: mvn clean package
    echo.
    pause
    goto menu
)

:: Сохраняем в БД
docker exec -i sql_trainer_postgres psql -U postgres -c "INSERT INTO teacher_settings (username, password_hash, full_name) VALUES ('%username%', '%hash%', '%fullname%');" 2>nul

echo.
echo ========================================
echo    ПРЕПОДАВАТЕЛЬ ДОБАВЛЕН!
echo ========================================
echo    Логин: %username%
echo    ФИО: %fullname%
echo    Пароль: %password%
echo    ВНИМАНИЕ: Сообщите пароль преподавателю!
echo.
pause
goto menu

:change_password
cls
echo ========================================
echo    СМЕНА ПАРОЛЯ
echo ========================================
echo.
docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО FROM teacher_settings ORDER BY username;" 2>nul
echo.
set /p username="Введите логин преподавателя: "
if "%username%"=="" (
    echo ОШИБКА: Логин не может быть пустым!
    pause
    goto menu
)

:: Проверяем, существует ли преподаватель
docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT username FROM teacher_settings WHERE username = '%username%';" 2>nul | findstr /i /c:"%username%" >nul
if %errorlevel% neq 0 (
    echo ОШИБКА: Преподаватель '%username%' НЕ НАЙДЕН!
    pause
    goto menu
)

:: Генерируем случайный пароль
set "chars=abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
set "new_password="
for /l %%i in (1,1,8) do (
    set /a "idx=!random! %% 62"
    for %%j in (!idx!) do set "new_password=!new_password!!chars:~%%j,1!"
)

echo.
echo Сгенерирован новый пароль: %new_password%

:: Генерируем хеш через Java (новый класс выводит только хеш)
set "hash="
for /f "delims=" %%i in ('java -cp "target\classes;target\SQLTrainer\WEB-INF\lib\jbcrypt-0.4.jar" com.sqltrainer.util.PasswordHashGenerator %new_password% 2^>nul') do set "hash=%%i"

if "%hash%"=="" (
    echo.
    echo ОШИБКА: Не удалось сгенерировать хеш!
    echo Убедитесь, что проект собран: mvn clean package
    echo.
    pause
    goto menu
)

:: Обновляем пароль
docker exec -i sql_trainer_postgres psql -U postgres -c "UPDATE teacher_settings SET password_hash = '%hash%', updated_at = CURRENT_TIMESTAMP WHERE username = '%username%';" 2>nul

echo.
echo ========================================
echo    ПАРОЛЬ ИЗМЕНЁН!
echo ========================================
echo    Логин: %username%
echo    Новый пароль: %new_password%
echo    ВНИМАНИЕ: Сообщите пароль преподавателю!
echo.
pause
goto menu

:delete_teacher
cls
echo ========================================
echo    УДАЛЕНИЕ ПРЕПОДАВАТЕЛЯ
echo ========================================
echo.
docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО FROM teacher_settings ORDER BY username;" 2>nul
echo.
set /p username="Введите логин преподавателя: "
if "%username%"=="" (
    echo ОШИБКА: Логин не может быть пустым!
    pause
    goto menu
)

:: Проверяем, существует ли преподаватель
docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT username FROM teacher_settings WHERE username = '%username%';" 2>nul | findstr /i /c:"%username%" >nul
if %errorlevel% neq 0 (
    echo ОШИБКА: Преподаватель '%username%' НЕ НАЙДЕН!
    pause
    goto menu
)

:: Проверяем, не последний ли преподаватель
for /f "delims=" %%i in ('docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT COUNT(*) FROM teacher_settings;" 2^>nul') do set "count=%%i"
set "count=!count: =!"
if !count! leq 1 (
    echo ОШИБКА: Нельзя удалить последнего преподавателя!
    pause
    goto menu
)

set /p confirm="Вы уверены, что хотите удалить '%username%'? (y/N): "
if /i not "!confirm!"=="y" (
    echo Отменено
    pause
    goto menu
)

docker exec -i sql_trainer_postgres psql -U postgres -c "DELETE FROM teacher_settings WHERE username = '%username%';" 2>nul
echo.
echo Преподаватель '%username%' удалён!
echo.
pause
goto menu

:exit
echo До свидания!
exit /b 0