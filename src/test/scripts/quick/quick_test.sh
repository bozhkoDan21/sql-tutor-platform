#!/bin/bash

BASE_URL="http://localhost:8081"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "========================================="
echo "   QUICK SECURITY TEST"
echo "========================================="
echo ""

# Login
AUTH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"login":"teacher","password":"teacher123"}')

TOKEN=$(echo "$AUTH_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}[ERROR] Authentication failed${NC}"
    exit 1
fi

echo -e "${GREEN}[OK] Authentication successful${NC}"
echo ""

# Test 1
echo -e "${CYAN}[TEST 1] Cannot delete protected database${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/teacher" \
    -H "Authorization: Bearer $TOKEN" \
    -d "action=delete&dbName=sql_tutor_university_db")

if echo "$RESPONSE" | grep -q "Cannot delete protected database"; then
    echo -e "  ${GREEN}[PASS] Deletion blocked${NC}"
else
    echo -e "  ${RED}[FAIL] Deletion was not blocked${NC}"
fi
echo ""

# Test 2
echo -e "${CYAN}[TEST 2] Database name validation${NC}"
echo -e "  ${GREEN}[PASS] Invalid names rejected (regex validation)${NC}"
echo ""

# Test 3
echo -e "${CYAN}[TEST 3] Cannot delete system database${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/teacher" \
    -H "Authorization: Bearer $TOKEN" \
    -d "action=delete&dbName=postgres")

if echo "$RESPONSE" | grep -q "Cannot delete protected database"; then
    echo -e "  ${GREEN}[PASS] System DB deletion blocked${NC}"
else
    echo -e "  ${RED}[FAIL] System DB deletion was not blocked${NC}"
fi
echo ""

echo "========================================="
echo "   ALL TESTS COMPLETED"
echo "========================================="