#!/bin/bash

BASE_URL="http://localhost:8081"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   XSS Protection Test"
echo "========================================="
echo ""

# 1. Проверка CSP заголовков
echo -e "${CYAN}[1/3] Checking CSP headers...${NC}"
RESPONSE=$(curl -s -i "$BASE_URL/index" 2>/dev/null)
CSP=$(echo "$RESPONSE" | grep -i "Content-Security-Policy" | head -1)
XSS_PROTECTION=$(echo "$RESPONSE" | grep -i "X-XSS-Protection" | head -1)

if [ -n "$CSP" ]; then
    echo -e "  ${GREEN}✅ CSP header present${NC}"
else
    echo -e "  ${RED}❌ CSP header missing!${NC}"
fi

if [ -n "$XSS_PROTECTION" ]; then
    echo -e "  ${GREEN}✅ X-XSS-Protection: $(echo "$XSS_PROTECTION" | cut -d: -f2- | xargs)${NC}"
else
    echo -e "  ${YELLOW}⚠️ X-XSS-Protection header missing${NC}"
fi

echo ""

# 2. Аутентификация преподавателя
echo -e "${CYAN}[2/3] Teacher authentication...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/login" \
    -H "Content-Type: application/json" \
    -d '{"password":"teacher123"}')

SUCCESS=$(echo "$LOGIN_RESPONSE" | grep -o '"success":[^,]*' | cut -d':' -f2)

if [ "$SUCCESS" = "true" ]; then
    echo -e "  ${GREEN}✅ Authentication successful${NC}"

    # Сохраняем cookies
    COOKIE_JAR=$(mktemp)
    curl -s -X POST "$BASE_URL/api/login" \
        -H "Content-Type: application/json" \
        -d '{"password":"teacher123"}' \
        -c "$COOKIE_JAR" > /dev/null
else
    echo -e "  ${RED}❌ Authentication failed${NC}"
    exit 1
fi

echo ""

# 3. Тест XSS через SQL запрос
echo -e "${CYAN}[3/3] Testing XSS via SQL query...${NC}"

# XSS payload в PostgreSQL (экранируем кавычки)
XSS_PAYLOAD="SELECT '<script>alert(''XSS'')</script>' as test"
QUERY_ENCODED=$(printf '%s' "$XSS_PAYLOAD" | jq -sRr @uri)

echo -e "  ${YELLOW}Query: $XSS_PAYLOAD${NC}"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/execute" \
    -b "$COOKIE_JAR" \
    -d "database=sql_tutor_university_db&query=$XSS_PAYLOAD")

# Проверяем результат
if echo "$RESPONSE" | grep -q '"success":true'; then
    echo -e "  ${GREEN}✅ Query executed successfully${NC}"

    # Извлекаем значение результата
    RESULT=$(echo "$RESPONSE" | grep -o '"test":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$RESULT" ]; then
        echo -e "  ${YELLOW}Result contains: $RESULT${NC}"

        # Проверяем, что результат не содержит выполняемый HTML
        if [[ "$RESULT" == *"<script>"* ]]; then
            echo -e "  ${GREEN}✅ XSS payload returned as plain text (safe)${NC}"
        else
            echo -e "  ${GRAY}Result: $RESULT${NC}"
        fi
    fi
else
    ERROR=$(echo "$RESPONSE" | grep -o '"error":"[^"]*"' | cut -d'"' -f4)
    echo -e "  ${RED}❌ Query failed: $ERROR${NC}"
fi

# Cleanup
rm -f "$COOKIE_JAR" 2>/dev/null

echo ""
echo "========================================="
echo "   MANUAL TEST (most important)"
echo "========================================="
echo ""
echo -e "${CYAN}1. Open: http://localhost:8081/index${NC}"
echo -e "${CYAN}2. Login with password: teacher123${NC}"
echo -e "${CYAN}3. Select any database${NC}"
echo -e "${CYAN}4. Execute this query:${NC}"
echo ""
echo -e "${YELLOW}   SELECT '<script>alert(\"XSS\")</script>' as test${NC}"
echo ""
echo -e "${GREEN}5. Expected: NO alert popup, text is displayed as is${NC}"
echo ""