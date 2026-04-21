# Базовые тесты безопасности OWASP

$BaseUrl = "http://localhost:8081"

Write-Host ""
Write-Host "========================================="
Write-Host "   SQL Trainer - Security Test"
Write-Host "========================================="
Write-Host ""

$tests = @(
    @{ name = "SQL Injection (OR 1=1)"; payload = "login=admin' OR '1'='1&password=anything" },
    @{ name = "SQL Injection (UNION)"; payload = "login=' UNION SELECT null,null--&password=x" },
    @{ name = "XSS (script tag)"; payload = "login=<script>alert('XSS')</script>&password=x" },
    @{ name = "Path Traversal"; payload = "../../../etc/passwd" }
)

foreach ($test in $tests) {
    Write-Host "Testing: $($test.name)" -ForegroundColor Cyan
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $test.payload -ContentType "application/x-www-form-urlencoded" -ErrorAction SilentlyContinue
        Write-Host "  Response: $($response | ConvertTo-Json -Compress)" -ForegroundColor Yellow
    } catch {
        Write-Host "  Blocked (error)" -ForegroundColor Green
    }
}