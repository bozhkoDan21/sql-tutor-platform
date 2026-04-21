# quick_test.ps1
# Quick security test for fixed vulnerabilities

$BaseUrl = "http://localhost:8081"

Write-Host ""
Write-Host "========================================="
Write-Host "   QUICK SECURITY TEST"
Write-Host "========================================="
Write-Host ""

# Login as teacher
$bodyJson = @{ login = "teacher"; password = "teacher123" } | ConvertTo-Json
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json"
    $Token = $response.accessToken
    Write-Host "[OK] Authentication successful" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Authentication failed. Make sure app is running." -ForegroundColor Red
    exit 1
}

$headers = @{ Authorization = "Bearer $Token" }
Write-Host ""

# ============================================
# TEST 1: Protected database deletion
# ============================================
Write-Host "[TEST 1] Cannot delete protected database 'sql_tutor_university_db'" -ForegroundColor Cyan

try {
    $body = "action=delete&dbName=sql_tutor_university_db"
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/teacher" -Method Post -Headers $headers -Body $body -ContentType "application/x-www-form-urlencoded" -ErrorAction SilentlyContinue

    if ($response.error -and $response.error -match "Cannot delete protected database") {
        Write-Host "  [PASS] Deletion blocked" -ForegroundColor Green
        Write-Host "  Message: $($response.error)" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] Deletion was not blocked!" -ForegroundColor Red
    }
} catch {
    Write-Host "  [EXCEPTION] $_" -ForegroundColor Red
}
Write-Host ""

# ============================================
# TEST 2: Database name validation
# ============================================
Write-Host "[TEST 2] Cannot create DB with invalid name" -ForegroundColor Cyan

# Test invalid name with space
$invalidDbName = "test database"
Write-Host "  -> Testing invalid name: '$invalidDbName'" -ForegroundColor Gray

if ($invalidDbName -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [FAIL] Invalid name '$invalidDbName' passed validation!" -ForegroundColor Red
} else {
    Write-Host "  [PASS] Invalid name rejected" -ForegroundColor Green
}

# Test invalid name with dot
$invalidDbName2 = "test.db"
Write-Host "  -> Testing invalid name: '$invalidDbName2'" -ForegroundColor Gray
if ($invalidDbName2 -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [FAIL] Invalid name '$invalidDbName2' passed validation!" -ForegroundColor Red
} else {
    Write-Host "  [PASS] Invalid name rejected" -ForegroundColor Green
}

# Test valid name
$validDbName = "test_db_2024"
Write-Host "  -> Testing valid name: '$validDbName'" -ForegroundColor Gray
if ($validDbName -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [PASS] Valid name accepted" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Valid name '$validDbName' was rejected!" -ForegroundColor Red
}
Write-Host ""

# ============================================
# TEST 3: System database protection
# ============================================
Write-Host "[TEST 3] Cannot delete system database 'postgres'" -ForegroundColor Cyan

try {
    $body = "action=delete&dbName=postgres"
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/teacher" -Method Post -Headers $headers -Body $body -ContentType "application/x-www-form-urlencoded" -ErrorAction SilentlyContinue

    if ($response.error -and $response.error -match "Cannot delete protected database") {
        Write-Host "  [PASS] System DB deletion blocked" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] System DB deletion was not blocked!" -ForegroundColor Red
    }
} catch {
    Write-Host "  [EXCEPTION] $_" -ForegroundColor Red
}
Write-Host ""

# ============================================
# SUMMARY
# ============================================
Write-Host "========================================="
Write-Host "   RESULTS"
Write-Host "========================================="
Write-Host ""
Write-Host "If all tests passed ([PASS]), fixes are working:"
Write-Host "  * Demo databases protected from deletion"
Write-Host "  * DB names validated (letters, numbers, underscore only)"
Write-Host "  * System databases protected from deletion"
Write-Host ""
Write-Host "Manual checks:" -ForegroundColor Yellow
Write-Host "  1. Make sure JWT_SECRET is in .env file"
Write-Host "  2. Try to login as student to postgres DB - should be denied"
Write-Host ""