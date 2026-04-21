# test_500_students.ps1
# Fixed version - sends JSON instead of form data

$BaseUrl = "http://localhost:8081"
$DbName = "sql_tutor_university_db"

Write-Host ""
Write-Host "========================================="
Write-Host "   SQL Trainer - Test with 500 students"
Write-Host "========================================="
Write-Host ""

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$StudentsFile = Join-Path $ScriptDir "students.txt"

if (-not (Test-Path $StudentsFile)) {
    Write-Host "ERROR: students.txt not found in $ScriptDir" -ForegroundColor Red
    exit 1
}

# Read students file - split by any whitespace
$Students = @()
Get-Content $StudentsFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -ne "") {
        $parts = $line -split '\s+'
        if ($parts.Count -ge 2) {
            $Students += @{
                login = $parts[0]
                password = $parts[1]
            }
        }
    }
}

$TotalStudents = $Students.Count
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  Server: $BaseUrl"
Write-Host "  Database: $DbName"
Write-Host "  Students count: $TotalStudents"
Write-Host ""

# ============================================
# TEST 1: Authentication
# ============================================
Write-Host "[1/4] Testing authentication..." -ForegroundColor Cyan

$SuccessAuth = 0
$FailAuth = 0
$AuthTimes = @()
$ProgressStep = [math]::Max(1, [int]($TotalStudents / 50))

for ($idx = 0; $idx -lt $Students.Count; $idx++) {
    $student = $Students[$idx]
    $login = $student.login
    $password = $student.password

    # Convert to JSON
    $bodyJson = @{ login = $login; password = $password } | ConvertTo-Json

    $StartTime = Get-Date
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json" -ErrorAction Stop
        $EndTime = Get-Date
        $Duration = [int](($EndTime - $StartTime).TotalMilliseconds)
        $AuthTimes += $Duration

        if ($response.accessToken) {
            $SuccessAuth++
            if ($idx % $ProgressStep -eq 0) { Write-Host "." -NoNewline }
        } else {
            $FailAuth++
            Write-Host "X" -NoNewline -ForegroundColor Red
        }
    } catch {
        $FailAuth++
        Write-Host "X" -NoNewline -ForegroundColor Red
        if ($FailAuth -le 5) {
            Write-Host ""
            Write-Host "  Error for $login : $_" -ForegroundColor DarkRed
        }
    }
}

Write-Host ""
Write-Host "OK: $SuccessAuth successful" -ForegroundColor Green
Write-Host "FAIL: $FailAuth errors" -ForegroundColor Red

if ($AuthTimes.Count -gt 0) {
    $Sum = 0
    foreach ($t in $AuthTimes) { $Sum += $t }
    $Avg = [int]($Sum / $AuthTimes.Count)
    $Min = ($AuthTimes | Measure-Object -Minimum).Minimum
    $Max = ($AuthTimes | Measure-Object -Maximum).Maximum

    Write-Host "  Avg auth time: ${Avg}ms"
    Write-Host "  Min/Max: ${Min}ms / ${Max}ms"
}
Write-Host ""

# ============================================
# TEST 2: Get token for first student
# ============================================
Write-Host "[2/4] Getting test token..." -ForegroundColor Cyan

if ($Students.Count -eq 0) {
    Write-Host "ERROR: No students found" -ForegroundColor Red
    exit 1
}

$FirstLogin = $Students[0].login
$FirstPassword = $Students[0].password

try {
    $bodyJson = @{ login = $FirstLogin; password = $FirstPassword } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json" -ErrorAction Stop
    $Token = $response.accessToken

    if (-not $Token) {
        Write-Host "ERROR: Failed to get token" -ForegroundColor Red
        exit 1
    }
    Write-Host "OK: Token obtained" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Authentication failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# ============================================
# TEST 3: Sequential queries
# ============================================
Write-Host "[3/4] Sequential queries (50 queries)..." -ForegroundColor Cyan

$QueryTimes = @()
$SuccessQueries = 0

for ($i = 1; $i -le 50; $i++) {
    $StartTime = Get-Date
    try {
        $headers = @{ Authorization = "Bearer $Token" }
        $body = "database=$DbName&query=SELECT * FROM student LIMIT 50"
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/execute" -Method Post -Headers $headers -Body $body -ContentType "application/x-www-form-urlencoded" -ErrorAction Stop
        $EndTime = Get-Date
        $Duration = [int](($EndTime - $StartTime).TotalMilliseconds)

        if ($response.success -eq $true) {
            $QueryTimes += $Duration
            $SuccessQueries++
            Write-Host "." -NoNewline
        } else {
            Write-Host "X" -NoNewline -ForegroundColor Red
        }
    } catch {
        Write-Host "X" -NoNewline -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "OK: $SuccessQueries/50 successful" -ForegroundColor Green

if ($QueryTimes.Count -gt 0) {
    $Sum = 0
    foreach ($t in $QueryTimes) { $Sum += $t }
    $Avg = [int]($Sum / $QueryTimes.Count)
    $Min = ($QueryTimes | Measure-Object -Minimum).Minimum
    $Max = ($QueryTimes | Measure-Object -Maximum).Maximum

    $Sorted = $QueryTimes | Sort-Object
    $P95Index = [math]::Floor($Sorted.Count * 0.95)
    if ($P95Index -ge $Sorted.Count) { $P95Index = $Sorted.Count - 1 }
    $P95 = $Sorted[$P95Index]

    Write-Host "  Avg query time: ${Avg}ms"
    Write-Host "  Min/Max: ${Min}ms / ${Max}ms"
    Write-Host "  95th percentile: ${P95}ms"
}
Write-Host ""

# ============================================
# TEST 4: Simple parallel (using Start-Job)
# ============================================
Write-Host "[4/4] Parallel queries (20 students)..." -ForegroundColor Cyan

$Jobs = @()
$ParallelCount = [math]::Min(20, $Students.Count)

Write-Host "Starting $ParallelCount parallel students..."
Write-Host -NoNewline "  Progress: "

for ($i = 0; $i -lt $ParallelCount; $i++) {
    $login = $Students[$i].login
    $password = $Students[$i].password

    $job = Start-Job -Name "student_$i" -ScriptBlock {
        param($url, $db, $login, $password)

        # Login with JSON
        $bodyJson = @{ login = $login; password = $password } | ConvertTo-Json
        try {
            $authResponse = Invoke-RestMethod -Uri "$url/api/auth/login" -Method Post -Body $bodyJson -ContentType "application/json" -ErrorAction Stop
            $token = $authResponse.accessToken

            if ($token) {
                $start = Get-Date
                $headers = @{ Authorization = "Bearer $token" }
                $queryBody = "database=$db&query=SELECT COUNT(*) FROM student"
                Invoke-RestMethod -Uri "$url/api/execute" -Method Post -Headers $headers -Body $queryBody -ContentType "application/x-www-form-urlencoded" -ErrorAction Stop | Out-Null
                $end = Get-Date
                $duration = [int](($end - $start).TotalMilliseconds)
                return $duration
            }
        } catch {
            return "FAIL"
        }
        return "FAIL"
    } -ArgumentList $BaseUrl, $DbName, $login, $password

    $Jobs += $job
    Write-Host "." -NoNewline
}

Write-Host ""
Write-Host "Waiting for completion..."

$ParallelTimes = @()
$FailCount = 0

foreach ($job in $Jobs) {
    $result = Receive-Job -Job $job -Wait -ErrorAction SilentlyContinue
    if ($result -ne "FAIL" -and $result -ne $null) {
        $ParallelTimes += $result
    } else {
        $FailCount++
    }
    Remove-Job -Job $job
}

Write-Host ""
if ($ParallelTimes.Count -gt 0) {
    $Sum = 0
    foreach ($t in $ParallelTimes) { $Sum += $t }
    $Avg = [int]($Sum / $ParallelTimes.Count)
    $Min = ($ParallelTimes | Measure-Object -Minimum).Minimum
    $Max = ($ParallelTimes | Measure-Object -Maximum).Maximum

    Write-Host "OK: $($ParallelTimes.Count)/$ParallelCount successful" -ForegroundColor Green
    Write-Host "FAIL: $FailCount errors" -ForegroundColor Red
    Write-Host ""
    Write-Host "Parallel statistics:" -ForegroundColor Cyan
    Write-Host "  Avg time: ${Avg}ms"
    Write-Host "  Min/Max: ${Min}ms / ${Max}ms"
}

Write-Host ""

# ============================================
# FINAL REPORT
# ============================================
Write-Host "========================================="
Write-Host "   FINAL REPORT"
Write-Host "========================================="
Write-Host ""

Write-Host "System processed:" -ForegroundColor Cyan
Write-Host "  * $SuccessAuth authentications"
Write-Host "  * $SuccessQueries sequential queries"
Write-Host "  * $($ParallelTimes.Count) parallel queries"
Write-Host ""

if ($SuccessAuth -eq $TotalStudents -and $SuccessQueries -eq 50) {
    Write-Host "SUCCESS: System is ready!" -ForegroundColor Green
} else {
    Write-Host "WARNING: System has errors. Check logs." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================="