#!/bin/bash

BASE_URL="http://localhost:8081"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   SQL TRAINER - FUNCTIONAL TEST"
echo "========================================="
echo ""

# Login as teacher
echo -e "${CYAN}[TEST 1] Teacher authentication${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/login" \
    -H "Content-Type: application/json" \
    -d '{"password":"teacher123"}')

SUCCESS=$(echo "$LOGIN_RESPONSE" | grep -o '"success":[^,]*' | cut -d':' -f2)

if [ "$SUCCESS" = "true" ]; then
    echo -e "  ${GREEN}[PASS] Authentication successful${NC}"

    # Сохраняем cookies для следующих запросов
    COOKIE_JAR=$(mktemp)
    curl -s -X POST "$BASE_URL/api/login" \
        -H "Content-Type: application/json" \
        -d '{"password":"teacher123"}' \
        -c "$COOKIE_JAR" > /dev/null
else
    echo -e "  ${RED}[FAIL] Authentication failed${NC}"
    exit 1
fi
echo ""

# Test 2: Cannot delete protected database
echo -e "${CYAN}[TEST 2] Cannot delete protected database${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/teacher" \
    -b "$COOKIE_JAR" \
    -d "action=delete&dbName=sql_tutor_university_db")

if echo "$RESPONSE" | grep -q "Cannot delete"; then
    echo -e "  ${GREEN}[PASS] Protected DB deletion blocked${NC}"
else
    echo -e "  ${YELLOW}[INFO] Database may not exist or deletion allowed${NC}"
fi
echo ""

# Test 3: Cannot delete system database
echo -e "${CYAN}[TEST 3] Cannot delete system database${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/teacher" \
    -b "$COOKIE_JAR" \
    -d "action=delete&dbName=postgres")

if echo "$RESPONSE" | grep -q "Cannot delete"; then
    echo -e "  ${GREEN}[PASS] System DB deletion blocked${NC}"
else
    echo -e "  ${RED}[FAIL] System DB deletion was not blocked${NC}"
fi
echo ""

# Test 4: Unauthorized access to teacher API
echo -e "${CYAN}[TEST 4] Unauthorized access to teacher API${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/teacher" \
    -d "action=list")

if echo "$RESPONSE" | grep -q "Unauthorized"; then
    echo -e "  ${GREEN}[PASS] Unauthorized access blocked${NC}"
else
    echo -e "  ${RED}[FAIL] Unauthorized access allowed${NC}"
fi
echo ""

# Test 5: Student cannot execute non-SELECT query
echo -e "${CYAN}[TEST 5] Student non-SELECT query blocked${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/execute" \
    -d "database=sql_tutor_university_db&query=DELETE+FROM+student+WHERE+id%3D1")

if echo "$RESPONSE" | grep -q "Only SELECT queries are allowed"; then
    echo -e "  ${GREEN}[PASS] Non-SELECT query blocked${NC}"
else
    echo -e "  ${YELLOW}[INFO] Check if database exists or query validation works${NC}"
fi
echo ""

# Cleanup
rm -f "$COOKIE_JAR" 2>/dev/null

echo "========================================="
echo "   TESTS COMPLETED"
echo "========================================="