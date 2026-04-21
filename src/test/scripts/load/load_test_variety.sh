#!/bin/bash

# load_test_variety.sh
# Load test for different query types

BASE_URL="http://localhost:8081"
DB_NAME="sql_tutor_university_db"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   SQL Trainer - Load Test (Variety)"
echo "========================================="
echo ""

# Login
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"login":"teacher","password":"teacher123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}ERROR: Failed to get token${NC}"
    exit 1
fi

# Query types
declare -a queries=(
    "Light (LIMIT 10)|SELECT * FROM student LIMIT 10"
    "Light (LIMIT 100)|SELECT * FROM student LIMIT 100"
    "Medium (COUNT)|SELECT COUNT(*) FROM student"
    "Medium (JOIN)|SELECT s.full_name, e.year_of_enrollment FROM student s JOIN enrollment e ON s.id = e.student_id LIMIT 100"
    "Heavy (GROUP BY)|SELECT faculty_id, COUNT(*) FROM enrollment GROUP BY faculty_id"
    "Heavy (AGGREGATE)|SELECT AVG(scholarship_amount) FROM enrollment WHERE scholarship_amount IS NOT NULL"
    "With Index (year filter)|SELECT COUNT(*) FROM enrollment WHERE year_of_enrollment = 2023"
    "With Index (birth filter)|SELECT COUNT(*) FROM student WHERE birth_date BETWEEN '\''2000-01-01'\'' AND '\''2000-12-31'\''"
    "Without Index (GROUP BY)|SELECT faculty_id, COUNT(*) FROM enrollment GROUP BY faculty_id"
)

echo -e "${CYAN}Testing queries...${NC}"
echo ""

results=()

for item in "${queries[@]}"; do
    name=$(echo "$item" | cut -d'|' -f1)
    sql=$(echo "$item" | cut -d'|' -f2)

    echo -n "Testing: $name... "

    times=()
    success=0

    for i in {1..20}; do
        start=$(date +%s%N)
        response=$(curl -s -X POST "$BASE_URL/api/execute" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            -d "database=$DB_NAME&query=$sql")
        end=$(date +%s%N)
        duration=$(( ($end - $start) / 1000000 ))

        if echo "$response" | grep -q '"success":true'; then
            times+=($duration)
            ((success++))
            echo -n "."
        else
            echo -n "X"
        fi
    done

    echo ""

    if [ ${#times[@]} -gt 0 ]; then
        avg=0
        min=${times[0]}
        max=${times[0]}
        sum=0
        for t in "${times[@]}"; do
            sum=$((sum + t))
            if [ $t -lt $min ]; then min=$t; fi
            if [ $t -gt $max ]; then max=$t; fi
        done
        avg=$((sum / ${#times[@]}))

        results+=("$name|$success/20|${avg}ms|${min}ms|${max}ms")
    fi
done

echo ""
echo "========================================="
echo "   RESULTS"
echo "========================================="
printf "%-25s %-10s %-10s %-10s %-10s\n" "Query" "Success" "Avg" "Min" "Max"
printf "%-25s %-10s %-10s %-10s %-10s\n" "-----" "-------" "---" "---" "---"

for result in "${results[@]}"; do
    name=$(echo "$result" | cut -d'|' -f1)
    success=$(echo "$result" | cut -d'|' -f2)
    avg=$(echo "$result" | cut -d'|' -f3)
    min=$(echo "$result" | cut -d'|' -f4)
    max=$(echo "$result" | cut -d'|' -f5)
    printf "%-25s %-10s %-10s %-10s %-10s\n" "$name" "$success" "$avg" "$min" "$max"
done

echo ""
echo "========================================="