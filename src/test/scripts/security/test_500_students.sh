#!/bin/bash

# Получаем директорию, где находится скрипт
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="http://localhost:8081"
DB_NAME="sql_tutor_university_db"

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   SQL Trainer - Test with 500 students"
echo "========================================="
echo ""

# Проверяем файл со студентами
STUDENTS_FILE="$SCRIPT_DIR/students.txt"
if [ ! -f "$STUDENTS_FILE" ]; then
    echo -e "${RED}ERROR: students.txt not found in $SCRIPT_DIR${NC}"
    exit 1
fi

# Подсчитываем количество студентов
TOTAL_STUDENTS=$(wc -l < "$STUDENTS_FILE")
echo -e "${CYAN}Configuration:${NC}"
echo "  Server: $BASE_URL"
echo "  Database: $DB_NAME"
echo "  Students count: $TOTAL_STUDENTS"
echo ""

# ============================================
# TEST 1: Authentication
# ============================================
echo -e "${BLUE}[1/4] Testing authentication of $TOTAL_STUDENTS students...${NC}"

SUCCESS_AUTH=0
FAIL_AUTH=0
AUTH_TIMES=()

while IFS=$'\t' read -r login password; do
    [ -z "$login" ] && continue

    # Build JSON body
    JSON_BODY="{\"login\":\"$login\",\"password\":\"$password\"}"

    START=$(date +%s%N)
    RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "$JSON_BODY")
    END=$(date +%s%N)
    DURATION=$((($END - $START) / 1000000))
    AUTH_TIMES+=($DURATION)

    if echo "$RESPONSE" | grep -q '"accessToken"'; then
        SUCCESS_AUTH=$((SUCCESS_AUTH + 1))
        echo -n "."
    else
        FAIL_AUTH=$((FAIL_AUTH + 1))
        echo -n "X"
    fi
done < "$STUDENTS_FILE"

echo ""
echo -e "${GREEN}OK: $SUCCESS_AUTH successful${NC}"
echo -e "${RED}FAIL: $FAIL_AUTH errors${NC}"

if [ ${#AUTH_TIMES[@]} -gt 0 ]; then
    TOTAL=0
    for t in "${AUTH_TIMES[@]}"; do
        TOTAL=$((TOTAL + t))
    done
    AVG=$((TOTAL / ${#AUTH_TIMES[@]}))

    MIN=${AUTH_TIMES[0]}
    MAX=${AUTH_TIMES[0]}
    for t in "${AUTH_TIMES[@]}"; do
        if [ $t -lt $MIN ]; then MIN=$t; fi
        if [ $t -gt $MAX ]; then MAX=$t; fi
    done

    echo "  Avg auth time: ${AVG}ms"
    echo "  Min/Max: ${MIN}ms / ${MAX}ms"
fi
echo ""

# ============================================
# TEST 2: Get token for first student
# ============================================
echo -e "${BLUE}[2/4] Getting test token...${NC}"

FIRST_STUDENT=$(head -1 "$STUDENTS_FILE")
FIRST_LOGIN=$(echo "$FIRST_STUDENT" | cut -f1)
FIRST_PASSWORD=$(echo "$FIRST_STUDENT" | cut -f2)

JSON_BODY="{\"login\":\"$FIRST_LOGIN\",\"password\":\"$FIRST_PASSWORD\"}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "$JSON_BODY")

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}ERROR: Failed to get token${NC}"
    exit 1
fi
echo -e "${GREEN}OK: Token obtained${NC}"
echo ""

# ============================================
# TEST 3: Sequential queries
# ============================================
echo -e "${BLUE}[3/4] Sequential queries (50 queries)...${NC}"

QUERY_TIMES=()
SUCCESS_QUERIES=0

for i in {1..50}; do
    START=$(date +%s%N)
    RESPONSE=$(curl -s -X POST "$BASE_URL/api/execute" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        --data-urlencode "database=$DB_NAME" \
        --data-urlencode "query=SELECT * FROM student LIMIT 50")
    END=$(date +%s%N)
    DURATION=$((($END - $START) / 1000000))

    if echo "$RESPONSE" | grep -q '"success":true'; then
        QUERY_TIMES+=($DURATION)
        SUCCESS_QUERIES=$((SUCCESS_QUERIES + 1))
        echo -n "."
    else
        echo -n "X"
    fi
done

echo ""
echo -e "${GREEN}OK: $SUCCESS_QUERIES/50 successful${NC}"

if [ ${#QUERY_TIMES[@]} -gt 0 ]; then
    TOTAL=0
    for t in "${QUERY_TIMES[@]}"; do
        TOTAL=$((TOTAL + t))
    done
    AVG=$((TOTAL / ${#QUERY_TIMES[@]}))

    SORTED=($(for t in "${QUERY_TIMES[@]}"; do echo "$t"; done | sort -n))
    MIN=${SORTED[0]}
    MAX=${SORTED[-1]}

    P95_IDX=$(( ${#SORTED[@]} * 95 / 100 ))
    [ $P95_IDX -eq ${#SORTED[@]} ] && P95_IDX=$(( ${#SORTED[@]} - 1 ))
    P95=${SORTED[$P95_IDX]}

    echo "  Avg query time: ${AVG}ms"
    echo "  Min/Max: ${MIN}ms / ${MAX}ms"
    echo "  95th percentile: ${P95}ms"
fi
echo ""

# ============================================
# TEST 4: Parallel queries
# ============================================
echo -e "${BLUE}[4/4] Parallel queries (20 students)...${NC}"

TEMP_RESULT=$(mktemp)

parallel_query() {
    local login=$1
    local password=$2
    local base_url=$3
    local db_name=$4

    JSON_BODY="{\"login\":\"$login\",\"password\":\"$password\"}"
    AUTH_RESPONSE=$(curl -s -X POST "$base_url/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "$JSON_BODY")

    TOKEN=$(echo "$AUTH_RESPONSE" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -n "$TOKEN" ]; then
        START=$(date +%s%N)
        curl -s -X POST "$base_url/api/execute" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            --data-urlencode "database=$db_name" \
            --data-urlencode "query=SELECT COUNT(*) FROM student" > /dev/null
        END=$(date +%s%N)
        DURATION=$((($END - $START) / 1000000))
        echo "$DURATION" >> "$TEMP_RESULT"
    else
        echo "FAIL" >> "$TEMP_RESULT"
    fi
}

echo "Starting 20 parallel students..."
echo -n "  Progress: "

# Берём первых 20 студентов
for i in $(seq 1 20); do
    STUDENT=$(sed -n "${i}p" "$STUDENTS_FILE")
    LOGIN=$(echo "$STUDENT" | cut -f1)
    PASSWORD=$(echo "$STUDENT" | cut -f2)
    parallel_query "$LOGIN" "$PASSWORD" "$BASE_URL" "$DB_NAME" &
    echo -n "."
done

wait
echo ""

PARALLEL_TIMES=()
FAIL_COUNT=0

while read line; do
    if [ "$line" != "FAIL" ] && [ -n "$line" ]; then
        PARALLEL_TIMES+=($line)
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
done < "$TEMP_RESULT"

rm -f "$TEMP_RESULT"

echo ""
if [ ${#PARALLEL_TIMES[@]} -gt 0 ]; then
    TOTAL=0
    for t in "${PARALLEL_TIMES[@]}"; do
        TOTAL=$((TOTAL + t))
    done
    AVG=$((TOTAL / ${#PARALLEL_TIMES[@]}))

    SORTED=($(for t in "${PARALLEL_TIMES[@]}"; do echo "$t"; done | sort -n))
    MIN=${SORTED[0]}
    MAX=${SORTED[-1]}

    P95_IDX=$(( ${#SORTED[@]} * 95 / 100 ))
    [ $P95_IDX -eq ${#SORTED[@]} ] && P95_IDX=$(( ${#SORTED[@]} - 1 ))
    P95=${SORTED[$P95_IDX]}

    echo -e "${GREEN}OK: ${#PARALLEL_TIMES[@]}/20 successful${NC}"
    echo -e "${RED}FAIL: $FAIL_COUNT errors${NC}"
    echo ""
    echo -e "${CYAN}Parallel statistics:${NC}"
    echo "  Avg time: ${AVG}ms"
    echo "  Min/Max: ${MIN}ms / ${MAX}ms"
    echo "  95th percentile: ${P95}ms"
fi

echo ""

# ============================================
# FINAL REPORT
# ============================================
echo "========================================="
echo "   FINAL REPORT"
echo "========================================="
echo ""

echo -e "${CYAN}System processed:${NC}"
echo "  * $SUCCESS_AUTH authentications"
echo "  * $SUCCESS_QUERIES sequential queries"
echo "  * ${#PARALLEL_TIMES[@]} parallel queries"
echo ""

if [ ${SUCCESS_AUTH} -eq ${TOTAL_STUDENTS} ] && [ ${SUCCESS_QUERIES} -eq 50 ] && [ ${FAIL_COUNT} -eq 0 ]; then
    echo -e "${GREEN}SUCCESS: System is ready!${NC}"
else
    echo -e "${YELLOW}WARNING: System has errors. Check logs.${NC}"
fi

echo ""
echo "========================================="