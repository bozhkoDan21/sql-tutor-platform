# load_test_variety.ps1
# Тест разных типов запросов

$BaseUrl = "http://localhost:8081"
$DbName = "sql_tutor_university_db"

Write-Host ""
Write-Host "========================================="
Write-Host "   SQL Trainer - Load Test (Variety)"
Write-Host "========================================="
Write-Host ""

# Логин
$bodyJson = @{ login = "teacher"; password = "teacher123" } | ConvertTo-Json
$response = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json"
$Token = $response.accessToken

if (-not $Token) {
    Write-Host "ERROR: Failed to get token" -ForegroundColor Red
    exit 1
}

# Типы запросов
$queries = @(
    @{ name = "Light (LIMIT 10)"; sql = "SELECT * FROM student LIMIT 10" },
    @{ name = "Light (LIMIT 100)"; sql = "SELECT * FROM student LIMIT 100" },
    @{ name = "Medium (COUNT)"; sql = "SELECT COUNT(*) FROM student" },
    @{ name = "Medium (JOIN)"; sql = "SELECT s.full_name, e.year_of_enrollment FROM student s JOIN enrollment e ON s.id = e.student_id LIMIT 100" },
    @{ name = "Heavy (GROUP BY)"; sql = "SELECT faculty_id, COUNT(*) FROM enrollment GROUP BY faculty_id" },
    @{ name = "Heavy (AGGREGATE)"; sql = "SELECT AVG(scholarship_amount) FROM enrollment WHERE scholarship_amount IS NOT NULL" },
    @{ name = "With Index (year filter)"; sql = "SELECT COUNT(*) FROM enrollment WHERE year_of_enrollment = 2023" },
    @{ name = "With Index (birth filter)"; sql = "SELECT COUNT(*) FROM student WHERE birth_date BETWEEN '2000-01-01' AND '2000-12-31'" },
    @{ name = "Without Index (GROUP BY)"; sql = "SELECT faculty_id, COUNT(*) FROM enrollment GROUP BY faculty_id" }
)

$results = @()

foreach ($q in $queries) {
    Write-Host "Testing: $($q.name)" -ForegroundColor Cyan

    $times = @()
    $success = 0

    for ($i = 1; $i -le 20; $i++) {
        $StartTime = Get-Date
        try {
            $headers = @{ Authorization = "Bearer $Token" }
            $body = "database=$DbName&query=$($q.sql)"
            $response = Invoke-RestMethod -Uri "$BaseUrl/api/execute" -Method Post -Headers $headers -Body $body -ContentType "application/x-www-form-urlencoded" -ErrorAction Stop
            $EndTime = Get-Date
            $Duration = [int](($EndTime - $StartTime).TotalMilliseconds)
            $times += $Duration
            $success++
            Write-Host "." -NoNewline
        } catch {
            Write-Host "X" -NoNewline -ForegroundColor Red
        }
    }

    Write-Host ""

    if ($times.Count -gt 0) {
        $avg = [int](($times | Measure-Object -Average).Average)
        $min = ($times | Measure-Object -Minimum).Minimum
        $max = ($times | Measure-Object -Maximum).Maximum

        $results += [PSCustomObject]@{
            Query = $q.name
            Success = "$success/20"
            Avg = "$avg ms"
            Min = "$min ms"
            Max = "$max ms"
        }
    }
}

Write-Host ""
Write-Host "========================================="
Write-Host "   RESULTS"
Write-Host "========================================="
$results | Format-Table -AutoSize