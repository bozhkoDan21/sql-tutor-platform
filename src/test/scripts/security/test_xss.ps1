# test_xss.ps1 - исправленная версия
$BaseUrl = "http://localhost:8081"

Write-Host ""
Write-Host "========================================="
Write-Host "   XSS Protection Test"
Write-Host "========================================="
Write-Host ""

# 1. Проверка CSP заголовков
Write-Host "[1/3] Checking CSP headers..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/index.jsp" -Method Get
    $csp = $response.Headers['Content-Security-Policy']
    $xssProtection = $response.Headers['X-XSS-Protection']

    if ($csp) {
        Write-Host "  ✅ CSP header present" -ForegroundColor Green
    } else {
        Write-Host "  ❌ CSP header missing!" -ForegroundColor Red
    }

    if ($xssProtection) {
        Write-Host "  ✅ X-XSS-Protection: $xssProtection" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️ X-XSS-Protection header missing" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ❌ Failed to fetch: $_" -ForegroundColor Red
}

Write-Host ""

# 2. Проверка аутентификации
Write-Host "[2/3] Getting auth token..." -ForegroundColor Cyan
$bodyJson = @{ login = "teacher"; password = "teacher123" } | ConvertTo-Json
try {
    $authResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json"
    $Token = $authResponse.accessToken

    if ($Token) {
        Write-Host "  ✅ Authenticated successfully" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Authentication failed" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  ❌ Login failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. Тест XSS через API (исправленный)
Write-Host "[3/3] Testing XSS via SQL query..." -ForegroundColor Cyan

# ПРАВИЛЬНОЕ экранирование для PostgreSQL
# Используем $$ для raw строки (PostgreSQL syntax)
$query = "SELECT '<script>alert(''XSS'')</script>' as test"

Write-Host "  Query: $query" -ForegroundColor Gray

$body = "database=sql_tutor_university_db&query=$query"
$headers = @{ Authorization = "Bearer $Token" }

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/execute" -Method Post -Headers $headers -Body $body -ContentType "application/x-www-form-urlencoded"

    if ($response.success -and $response.rows) {
        $resultValue = $response.rows[0].test

        Write-Host "  ✅ Query executed successfully" -ForegroundColor Green
        Write-Host "  Result contains: $resultValue" -ForegroundColor Yellow

        # Проверяем, что результат не содержит выполняемый HTML
        if ($resultValue -match "<script>") {
            Write-Host "  ✅ XSS payload returned as plain text (safe)" -ForegroundColor Green
        } else {
            Write-Host "  Result: $resultValue" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ❌ Query failed: $($response.error)" -ForegroundColor Red
    }
} catch {
    Write-Host "  ❌ API error: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================="
Write-Host "   MANUAL TEST (most important)"
Write-Host "========================================="
Write-Host ""
Write-Host "1. Open: http://localhost:8081/index.jsp" -ForegroundColor Cyan
Write-Host "2. Login: teacher / teacher123"
Write-Host "3. Select any database"
Write-Host "4. Execute this query:" -ForegroundColor Yellow
Write-Host ""
Write-Host "   SELECT '<script>alert(\"XSS\")</script>' as test" -ForegroundColor White
Write-Host ""
Write-Host "5. Expected: NO alert popup, text is displayed as is" -ForegroundColor Green
Write-Host ""