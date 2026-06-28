#!/bin/bash
# ================================================================
# УПРАВЛЕНИЕ ПРЕПОДАВАТЕЛЯМИ (Linux)
# ================================================================
# Запуск: ./manage_teachers.sh
# ================================================================

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Переходим в корень проекта
cd "$(dirname "$0")/../.." || exit 1

# Проверяем, что проект собран
if [ ! -f "target/classes/com/sqltrainer/util/PasswordHashGenerator.class" ]; then
    echo ""
    echo -e "${YELLOW}ВНИМАНИЕ: Проект не собран!${NC}"
    echo "Выполните: mvn clean package"
    echo ""
    read -p "Нажмите Enter для продолжения..."
fi

# ================================================================
# ФУНКЦИЯ: ГЕНЕРАЦИЯ СЛУЧАЙНОГО ПАРОЛЯ
# ================================================================
generate_password() {
    cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 8 | head -n 1
}

# ================================================================
# ФУНКЦИЯ: ГЕНЕРАЦИЯ BCrypt-ХЕША
# ================================================================
generate_hash() {
    local password="$1"
    local hash=""
    
    # Пробуем через Java
    if [ -f "target/classes/com/sqltrainer/util/PasswordHashGenerator.class" ] && [ -f "target/SQLTrainer/WEB-INF/lib/jbcrypt-0.4.jar" ]; then
        hash=$(java -cp "target/classes:target/SQLTrainer/WEB-INF/lib/jbcrypt-0.4.jar" com.sqltrainer.util.PasswordHashGenerator "$password" 2>/dev/null)
    fi
    
    echo "$hash"
}

# ================================================================
# ФУНКЦИЯ: ПРОВЕРКА СУЩЕСТВОВАНИЯ ПРЕПОДАВАТЕЛЯ
# ================================================================
teacher_exists() {
    local username="$1"
    local result
    result=$(docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT username FROM teacher_settings WHERE username = '$username';" 2>/dev/null | xargs)
    if [ -n "$result" ]; then
        return 0
    else
        return 1
    fi
}

# ================================================================
# МЕНЮ
# ================================================================
while true; do
    clear
    echo "========================================"
    echo "   УПРАВЛЕНИЕ ПРЕПОДАВАТЕЛЯМИ"
    echo "========================================"
    echo ""
    echo "  1. Просмотреть список преподавателей"
    echo "  2. Добавить преподавателя"
    echo "  3. Сбросить пароль"
    echo "  4. Удалить преподавателя"
    echo "  5. Выход"
    echo ""
    read -p "Выберите действие (1-5): " choice

    case $choice in
        1)
            clear
            echo "========================================"
            echo "   СПИСОК ПРЕПОДАВАТЕЛЕЙ"
            echo "========================================"
            echo ""
            docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО, created_at AS Создан FROM teacher_settings ORDER BY username;" 2>/dev/null
            echo ""
            read -p "Нажмите Enter для продолжения..."
            ;;
            
        2)
            clear
            echo "========================================"
            echo "   ДОБАВЛЕНИЕ ПРЕПОДАВАТЕЛЯ"
            echo "========================================"
            echo ""
            
            read -p "Введите логин преподавателя: " username
            if [ -z "$username" ]; then
                echo -e "${RED}ОШИБКА: Логин не может быть пустым!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Проверяем, существует ли преподаватель
            if teacher_exists "$username"; then
                echo -e "${RED}ОШИБКА: Преподаватель '$username' УЖЕ СУЩЕСТВУЕТ!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            read -p "Введите ФИО преподавателя: " fullname
            if [ -z "$fullname" ]; then
                fullname="$username"
            fi
            
            # Генерируем пароль
            password=$(generate_password)
            echo ""
            echo -e "${BLUE}Сгенерирован пароль: $password${NC}"
            
            # Генерируем хеш
            hash=$(generate_hash "$password")
            if [ -z "$hash" ]; then
                echo ""
                echo -e "${RED}ОШИБКА: Не удалось сгенерировать хеш!${NC}"
                echo "Убедитесь, что проект собран: mvn clean package"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Сохраняем в БД
            docker exec -i sql_trainer_postgres psql -U postgres << EOF
INSERT INTO teacher_settings (username, password_hash, full_name)
VALUES ('$username', '$hash', '$fullname');
EOF
            
            echo ""
            echo "========================================"
            echo -e "${GREEN}   ПРЕПОДАВАТЕЛЬ ДОБАВЛЕН!${NC}"
            echo "========================================"
            echo -e "   ${GREEN}Логин:${NC} $username"
            echo -e "   ${GREEN}ФИО:${NC} $fullname"
            echo -e "   ${YELLOW}Пароль:${NC} $password"
            echo -e "   ${YELLOW}ВНИМАНИЕ: Сообщите пароль преподавателю!${NC}"
            echo ""
            read -p "Нажмите Enter для продолжения..."
            ;;
            
        3)
            clear
            echo "========================================"
            echo "   СМЕНА ПАРОЛЯ"
            echo "========================================"
            echo ""
            
            docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО FROM teacher_settings ORDER BY username;" 2>/dev/null
            echo ""
            
            read -p "Введите логин преподавателя: " username
            if [ -z "$username" ]; then
                echo -e "${RED}ОШИБКА: Логин не может быть пустым!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Проверяем, существует ли преподаватель
            if ! teacher_exists "$username"; then
                echo -e "${RED}ОШИБКА: Преподаватель '$username' НЕ НАЙДЕН!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Генерируем новый пароль
            new_password=$(generate_password)
            echo ""
            echo -e "${BLUE}Сгенерирован новый пароль: $new_password${NC}"
            
            # Генерируем хеш
            hash=$(generate_hash "$new_password")
            if [ -z "$hash" ]; then
                echo ""
                echo -e "${RED}ОШИБКА: Не удалось сгенерировать хеш!${NC}"
                echo "Убедитесь, что проект собран: mvn clean package"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Обновляем пароль
            docker exec -i sql_trainer_postgres psql -U postgres << EOF
UPDATE teacher_settings 
SET password_hash = '$hash', updated_at = CURRENT_TIMESTAMP 
WHERE username = '$username';
EOF
            
            echo ""
            echo "========================================"
            echo -e "${GREEN}   ПАРОЛЬ ИЗМЕНЁН!${NC}"
            echo "========================================"
            echo -e "   ${GREEN}Логин:${NC} $username"
            echo -e "   ${YELLOW}Новый пароль:${NC} $new_password"
            echo -e "   ${YELLOW}ВНИМАНИЕ: Сообщите пароль преподавателю!${NC}"
            echo ""
            read -p "Нажмите Enter для продолжения..."
            ;;
            
        4)
            clear
            echo "========================================"
            echo "   УДАЛЕНИЕ ПРЕПОДАВАТЕЛЯ"
            echo "========================================"
            echo ""
            
            docker exec sql_trainer_postgres psql -U postgres -c "SELECT username AS Логин, full_name AS ФИО FROM teacher_settings ORDER BY username;" 2>/dev/null
            echo ""
            
            read -p "Введите логин преподавателя: " username
            if [ -z "$username" ]; then
                echo -e "${RED}ОШИБКА: Логин не может быть пустым!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Проверяем, существует ли преподаватель
            if ! teacher_exists "$username"; then
                echo -e "${RED}ОШИБКА: Преподаватель '$username' НЕ НАЙДЕН!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            # Проверяем, не последний ли преподаватель
            count=$(docker exec sql_trainer_postgres psql -U postgres -t -c "SELECT COUNT(*) FROM teacher_settings;" 2>/dev/null | xargs)
            if [ "$count" -le 1 ]; then
                echo -e "${RED}ОШИБКА: Нельзя удалить последнего преподавателя!${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            read -p "Вы уверены, что хотите удалить '$username'? (y/N): " confirm
            if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
                echo -e "${RED}Отменено${NC}"
                read -p "Нажмите Enter для продолжения..."
                continue
            fi
            
            docker exec -i sql_trainer_postgres psql -U postgres << EOF
DELETE FROM teacher_settings WHERE username = '$username';
EOF
            
            echo ""
            echo -e "${GREEN}Преподаватель '$username' удалён!${NC}"
            echo ""
            read -p "Нажмите Enter для продолжения..."
            ;;
            
        5)
            echo "До свидания!"
            exit 0
            ;;
            
        *)
            echo -e "${RED}Неверный выбор!${NC}"
            read -p "Нажмите Enter для продолжения..."
            ;;
    esac
done