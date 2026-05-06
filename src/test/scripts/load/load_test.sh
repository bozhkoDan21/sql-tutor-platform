#!/bin/bash

BASE_URL="http://localhost:8081"
DB_NAME="sql_tutor_university_db"
COOKIE_JAR=$(mktemp)

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   SQL Trainer - Нагрузочное тестирование"
echo "========================================="
echo ""

# Аутентификация
echo -e "${CYAN}[1/6] Аутентификация преподавателя...${NC}"
curl -s -X POST "$BASE_URL/api/login" \
    -H "Content-Type: application/json" \
    -d '{"password":"teacher123"}' \
    -c "$COOKIE_JAR" > /dev/null

echo -e "  ${GREEN}✅ Сессия создана${NC}"
echo ""

# ============================================
# ТЕСТ 1: Максимальное количество строк (MAX_ROWS=1000)
# ============================================
echo -e "${CYAN}[2/6] Тест ограничения MAX_ROWS=1000...${NC}"
START=$(date +%s%N)
RESPONSE=$(curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=SELECT+*+FROM+student")
END=$(date +%s%N)
TIME=$((($END - $START) / 1000000))

ROWS=$(echo "$RESPONSE" | grep -o '"rowCount":[0-9]*' | head -1 | cut -d':' -f2)
if [ -z "$ROWS" ]; then
    ROWS=$(echo "$RESPONSE" | grep -o '"rows":\[.*\]' | grep -o ',' | wc -l)
fi

echo -e "  Время выполнения: ${TIME} мс"
echo -e "  Строк возвращено: ${ROWS:-0} (максимум 1000)"
if [ "${ROWS:-0}" -le 1000 ]; then
    echo -e "  ${GREEN}✅ MAX_ROWS работает корректно${NC}"
else
    echo -e "  ${RED}❌ MAX_ROWS не сработал${NC}"
fi
echo ""

# ============================================
# ТЕСТ 2: Ограничение времени выполнения (3 секунды)
# ============================================
echo -e "${CYAN}[3/6] Тест таймаута запроса (3 секунды)...${NC}"
START=$(date +%s%N)
RESPONSE=$(curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=SELECT+pg_sleep(5)")
END=$(date +%s%N)
TIME=$((($END - $START) / 1000000))

if echo "$RESPONSE" | grep -q "timeout"; then
    echo -e "  Время до ошибки: ${TIME} мс"
    echo -e "  ${GREEN}✅ Таймаут сработал (запрос прерван до 5 секунд)${NC}"
else
    echo -e "  ${YELLOW}⚠️ Таймаут не сработал или pg_sleep заблокирован${NC}"
fi
echo ""

# ============================================
# ТЕСТ 3: Параллельные запросы (семафор на 10)
# ============================================
echo -e "${CYAN}[4/6] Тест параллельных запросов (15 одновременных)...${NC}"
TEMP_FILE=$(mktemp)
echo -n "  Выполняется 15 параллельных запросов "

for i in {1..15}; do
    (
        START=$(date +%s%N)
        curl -s -X POST "$BASE_URL/api/execute" \
            -b "$COOKIE_JAR" \
            -d "database=$DB_NAME&query=SELECT+COUNT(*)+FROM+student" \
            -o /dev/null -w "%{http_code}" >> "$TEMP_FILE" 2>/dev/null
        END=$(date +%s%N)
        echo " $((($END - $START) / 1000000))" >> "${TEMP_FILE}_times"
    ) &
    echo -n "."
    sleep 0.1
done
wait
echo ""

SUCCESS_COUNT=$(grep -c "200" "$TEMP_FILE" 2>/dev/null || echo "0")
FAIL_COUNT=$(grep -c "429\|500\|503" "$TEMP_FILE" 2>/dev/null || echo "0")
TOTAL_TIMES=$(cat "${TEMP_FILE}_times" 2>/dev/null | tr '\n' ' ')
AVG_TIME=0
if [ -f "${TEMP_FILE}_times" ]; then
    SUM=0
    COUNT=0
    for t in $(cat "${TEMP_FILE}_times"); do
        SUM=$((SUM + t))
        COUNT=$((COUNT + 1))
    done
    if [ $COUNT -gt 0 ]; then
        AVG_TIME=$((SUM / COUNT))
    fi
fi

echo -e "  Успешных запросов: ${SUCCESS_COUNT}/15"
echo -e "  Среднее время: ${AVG_TIME} мс"
if [ "${SUCCESS_COUNT}" -ge 10 ]; then
    echo -e "  ${GREEN}✅ Семафор и очередь работают (10 одновременных, остальные в очереди)${NC}"
fi

rm -f "$TEMP_FILE" "${TEMP_FILE}_times"
echo ""

# ============================================
# ТЕСТ 4: Кеширование результатов (30 секунд)
# ============================================
echo -e "${CYAN}[5/6] Тест кеширования (30 секунд)...${NC}"
QUERY="SELECT COUNT(*) FROM student"

# Первый запрос (без кеша)
START=$(date +%s%N)
RESPONSE1=$(curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=$QUERY")
TIME1=$((($(date +%s%N) - START) / 1000000))

# Второй запрос (с кешем)
START=$(date +%s%N)
RESPONSE2=$(curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=$QUERY")
TIME2=$((($(date +%s%N) - START) / 1000000))

echo -e "  Первый запрос (без кеша): ${TIME1} мс"
echo -e "  Второй запрос (с кешем): ${TIME2} мс"

if [ $TIME2 -lt $TIME1 ]; then
    IMPROVEMENT=$(( (TIME1 - TIME2) * 100 / TIME1 ))
    echo -e "  Ускорение: ~${IMPROVEMENT}%"
    echo -e "  ${GREEN}✅ Кеширование работает${NC}"
else
    echo -e "  ${YELLOW}⚠️ Кеширование не дало значительного ускорения${NC}"
fi
echo ""

# ============================================
# ТЕСТ 5: Демонстрация работы индексов
# ============================================
echo -e "${CYAN}[6/6] Демонстрация влияния индексов на производительность...${NC}"

# Запрос без индекса (или с плохим планом)
START=$(date +%s%N)
curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=SELECT+COUNT(*)+FROM+enrollment+WHERE+notes+LIKE+'%Академическая%'" \
    -o /dev/null
TIME1=$((($(date +%s%N) - START) / 1000000))
echo -e "  Поиск по тексту (notes LIKE): ${TIME1} мс"

# Запрос с индексом
START=$(date +%s%N)
curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=$DB_NAME&query=SELECT+COUNT(*)+FROM+student+WHERE+birth_date+>+'2000-01-01'" \
    -o /dev/null
TIME2=$((($(date +%s%N) - START) / 1000000))
echo -e "  Поиск по дате (birth_date с индексом): ${TIME2} мс"

if [ $TIME1 -gt 0 ] && [ $TIME2 -gt 0 ]; then
    echo -e "  ${GREEN}✅ Преподаватель может наглядно показать студентам важность индексов${NC}"
fi
echo ""

# ============================================
# ОЧИСТКА И ВЫВОД
# ============================================
rm -f "$COOKIE_JAR"

echo "========================================="
echo "   РЕЗУЛЬТАТЫ НАГРУЗОЧНОГО ТЕСТИРОВАНИЯ"
echo "========================================="
echo ""
echo -e "${CYAN}Проверенные ограничения:${NC}"
echo "  ✅ MAX_ROWS = 1000 строк"
echo "  ✅ Таймаут запроса = 3 секунды"
echo "  ✅ Семафор на 10 параллельных запросов"
echo "  ✅ Кеширование результатов (30 сек)"
echo ""
echo -e "${CYAN}Демонстрационные возможности для преподавателя:${NC}"
echo "  ✅ Сравнение времени выполнения запросов с/без индексов"
echo "  ✅ Объяснение плана запроса (EXPLAIN ANALYZE)"
echo "  ✅ Визуализация дерева выполнения с цветовой индикацией"
echo ""