#!/bin/bash

# security_test.sh
# OWASP Security Test for SQL Trainer

BASE_URL="http://localhost:8081"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   SQL Trainer - Security Test"
echo "========================================="
echo ""

# Test cases
declare -a tests=(
    "SQL Injection (OR 1=1)|login=admin' OR '1'='1&password=anything"
    "SQL Injection (UNION)|login=' UNION SELECT null,null--&password=x"
    "XSS (script tag)|login=<script>alert('XSS')</script>&password=x"
    "Path Traversal|login=../../../etc/passwd&password=x"
    "Long Input|login=$(printf 'A%.0s' {1..10000})&password=x"
    "Null Byte|login=admin%00&password=x"
    "JSON Injection|login={\"admin\":true}&password=x"
)

for test in "${tests[@]}"; do
    name=$(echo "$test" | cut -d'|' -f1)
    payload=$(echo "$test" | cut -d'|' -f2)

    echo -e "${CYAN}Testing: $name${NC}"

    response=$(curl -s -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "$payload" \
        -w "%{http_code}" 2>/dev/null)

    http_code="${response: -3}"

    if [ "$http_code" = "401" ] || [ "$http_code" = "400" ] || [ "$http_code" = "403" ] || [ "$http_code" = "500" ]; then
        echo -e "  ${GREEN}Blocked (HTTP $http_code)${NC}"
    elif [ "$http_code" = "200" ]; then
        echo -e "  ${RED}WARNING: Request succeeded (HTTP 200) - possible vulnerability!${NC}"
    else
        echo -e "  ${YELLOW}Response: HTTP $http_code${NC}"
    fi
    echo ""
done

# Additional test: SQL injection on /api/execute endpoint
echo -e "${CYAN}Additional test: SQL injection on /api/execute endpoint${NC}"

# First get token
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"login":"teacher","password":"teacher123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
    echo -e "${GREEN}Token obtained${NC}"

    sql_payloads=(
        "SELECT * FROM student; DROP TABLE student; --"
        "SELECT * FROM student WHERE id = 1 OR 1=1"
        "1' OR '1'='1"
        "' UNION SELECT null,null,null--"
    )

    for payload in "${sql_payloads[@]}"; do
        echo -n "  Testing: $payload... "

        response=$(curl -s -X POST "$BASE_URL/api/execute" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            -d "database=sql_tutor_university_db&query=$payload")

        if echo "$response" | grep -q "prohibited\|syntax error\|Only SELECT"; then
            echo -e "${GREEN}Blocked${NC}"
        else
            echo -e "${RED}WARNING: Not blocked!${NC}"
        fi
    done
else
    echo -e "${RED}Failed to get token${NC}"
fi

echo ""
echo "========================================="
echo "   SECURITY TEST COMPLETED"
echo "========================================="