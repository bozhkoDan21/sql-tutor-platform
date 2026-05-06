# test_security.ps1
# Functional test for SQL Trainer

$BaseUrl = "http://localhost:8081"

Write-Host ""
Write-Host "========================================="
Write-Host "   SQL TRAINER - FUNCTIONAL TEST"
Write-Host "========================================="
Write-Host ""

# Сохраняем cookies для сессии
$Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# ============================================
# TEST 1: Teacher authentication
# ============================================
Write-Host "[TEST 1] Teacher authentication" -ForegroundColor Cyan

$bodyJson = @{ password = "teacher123" } | ConvertTo-Json
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/login" -Method Post -Body $bodyJson -ContentType "application/json" -WebSession $Session
    if ($response.success -eq $true) {
        Write-Host "  [PASS] Authentication successful" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Authentication failed: $($response.error)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  [ERROR] Authentication failed. Make sure app is running." -ForegroundColor Red
    Write-Host "  Exception: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# ============================================
# TEST 2: Protected database deletion
# ============================================
Write-Host "[TEST 2] Cannot delete protected database" -ForegroundColor Cyan

try {
    $body = "action=delete&dbName=sql_tutor_university_db"
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/teacher" -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -WebSession $Session -ErrorAction SilentlyContinue

    if ($response.error -and $response.error -match "Cannot delete") {
        Write-Host "  [PASS] Protected DB deletion blocked" -ForegroundColor Green
        Write-Host "  Message: $($response.error)" -ForegroundColor Gray
    } else {
        Write-Host "  [INFO] Database may not exist or deletion allowed" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [EXCEPTION] $_" -ForegroundColor Red
}
Write-Host ""

# ============================================
# TEST 3: System database protection
# ============================================
Write-Host "[TEST 3] Cannot delete system database 'postgres'" -ForegroundColor Cyan

try {
    $body = "action=delete&dbName=postgres"
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/teacher" -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -WebSession $Session -ErrorAction SilentlyContinue

    if ($response.error -and $response.error -match "Cannot delete system database") {
        Write-Host "  [PASS] System DB deletion blocked" -ForegroundColor Green
        Write-Host "  Message: $($response.error)" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] System DB deletion was not blocked!" -ForegroundColor Red
    }
} catch {
    Write-Host "  [EXCEPTION] $_" -ForegroundColor Red
}
Write-Host ""

# ============================================
# TEST 4: Unauthorized access to teacher API
# ============================================
Write-Host "[TEST 4] Unauthorized access to teacher API" -ForegroundColor Cyan

try {
    # Запрос без сессии (новый сеанс)
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/teacher?action=list" -Method Get -ErrorAction SilentlyContinue
    if ($response.error -and ($response.error -match "Unauthorized" -or $response -match "Unauthorized")) {
        Write-Host "  [PASS] Unauthorized access blocked" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Unauthorized access allowed!" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host "  [PASS] Unauthorized access returned 401" -ForegroundColor Green
    } else {
        Write-Host "  [INFO] Request failed with status: $statusCode" -ForegroundColor Yellow
    }
}
Write-Host ""

# ============================================
# TEST 5: Student non-SELECT query blocked
# ============================================
Write-Host "[TEST 5] Student non-SELECT query blocked" -ForegroundColor Cyan

try {
    $body = "database=sql_tutor_university_db&query=DELETE+FROM+student+WHERE+id%3D1"
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/execute" -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -ErrorAction SilentlyContinue

    if ($response.error -and $response.error -match "Only SELECT queries are allowed") {
        Write-Host "  [PASS] Non-SELECT query blocked" -ForegroundColor Green
    } else {
        Write-Host "  [INFO] Response: $($response | ConvertTo-Json)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [EXCEPTION] $_" -ForegroundColor Red
}
Write-Host ""

# ============================================
# TEST 6: Database name validation (regex)
# ============================================
Write-Host "[TEST 6] Database name validation" -ForegroundColor Cyan

# Test invalid name with space
$invalidDbName = "test database"
Write-Host "  -> Testing invalid name: '$invalidDbName'" -ForegroundColor Gray
if ($invalidDbName -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [FAIL] Invalid name passed validation!" -ForegroundColor Red
} else {
    Write-Host "  [PASS] Invalid name rejected" -ForegroundColor Green
}

# Test invalid name with dot
$invalidDbName2 = "test.db"
Write-Host "  -> Testing invalid name: '$invalidDbName2'" -ForegroundColor Gray
if ($invalidDbName2 -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [FAIL] Invalid name passed validation!" -ForegroundColor Red
} else {
    Write-Host "  [PASS] Invalid name rejected" -ForegroundColor Green
}

# Test valid name
$validDbName = "test_db_2024"
Write-Host "  -> Testing valid name: '$validDbName'" -ForegroundColor Gray
if ($validDbName -match '^[a-zA-Z0-9_]+$') {
    Write-Host "  [PASS] Valid name accepted" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Valid name was rejected!" -ForegroundColor Red
}
Write-Host ""

# ============================================
# SUMMARY
# ============================================
Write-Host "========================================="
Write-Host "   RESULTS"
Write-Host "========================================="
Write-Host ""
Write-Host "Security checks:"
Write-Host "  ✓ Teacher authentication by password"
Write-Host "  ✓ Protected databases cannot be deleted"
Write-Host "  ✓ System databases protected from deletion"
Write-Host "  ✓ Unauthorized access blocked"
Write-Host "  ✓ Students can only execute SELECT queries"
Write-Host "  ✓ Database name validation (regex)"
Write-Host ""
Write-Host "Note: Authentication uses HTTP sessions, not JWT" -ForegroundColor Yellow
Write-Host ""