# test_xss.ps1
$BaseUrl = "http://localhost:8081"

Write-Host ""
Write-Host "========================================="
Write-Host "   XSS Protection Test"
Write-Host "========================================="
Write-Host ""

# Сохраняем cookies для сессии
$Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Проверка CSP заголовков
Write-Host "[1/3] Checking CSP headers..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/index" -Method Get -WebSession $Session
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

# 2. Аутентификация преподавателя
Write-Host "[2/3] Teacher authentication..." -ForegroundColor Cyan
$bodyJson = @{ password = "teacher123" } | ConvertTo-Json
try {
    $authResponse = Invoke-RestMethod -Uri "$BaseUrl/api/login" -Method Post -Body $bodyJson -ContentType "application/json" -WebSession $Session
    if ($authResponse.success -eq $true) {
        Write-Host "  ✅ Authentication successful" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Authentication failed" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  ❌ Login failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. Тест XSS через SQL запрос
Write-Host "[3/3] Testing XSS via SQL query..." -ForegroundColor Cyan

$query = "SELECT '<script>alert(''XSS'')</script>' as test"
Write-Host "  Query: $query" -ForegroundColor Gray

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/execute" -Method Post -WebSession $Session -Body "database=sql_tutor_university_db&query=$query" -ContentType "application/x-www-form-urlencoded"

    if ($response.success -eq $true) {
        Write-Host "  ✅ Query executed successfully" -ForegroundColor Green
        $resultValue = $response.rows[0].test
        Write-Host "  Result contains: $resultValue" -ForegroundColor Yellow

        if ($resultValue -match "<script>") {
            Write-Host "  ✅ XSS payload returned as plain text (safe)" -ForegroundColor Green
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
Write-Host "1. Open: http://localhost:8081/index" -ForegroundColor Cyan
Write-Host "2. Login with password: teacher123" -ForegroundColor Cyan
Write-Host "3. Select any database"
Write-Host "4. Execute this query:" -ForegroundColor Yellow
Write-Host ""
Write-Host "   SELECT '<script>alert(\"XSS\")</script>' as test" -ForegroundColor White
Write-Host ""
Write-Host "5. Expected: NO alert popup, text is displayed as is" -ForegroundColor Green
Write-Host ""