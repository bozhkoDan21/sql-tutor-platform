<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <title>SQL Trainer - Панель преподавателя</title>
    <link rel="stylesheet" href="style.css?v=2">
    <style>
        .loading-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: none;
            justify-content: center;
            align-items: center;
            z-index: 1000;
            backdrop-filter: blur(3px);
        }
        .loading-spinner {
            background: white;
            padding: 2rem;
            border-radius: 1rem;
            text-align: center;
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
            max-width: 500px;
            width: 90%;
        }
        .spinner {
            width: 50px;
            height: 50px;
            border: 5px solid var(--primary-light);
            border-top: 5px solid var(--primary);
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 1rem;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        .progress-bar {
            width: 100%;
            height: 20px;
            background: var(--light);
            border-radius: 10px;
            overflow: hidden;
            margin: 1rem 0;
        }
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, var(--primary), var(--primary-hover));
            width: 0%;
            transition: width 0.3s ease;
        }
        .loading-text {
            color: var(--text);
            font-size: 1.1rem;
            margin-top: 0.5rem;
            font-weight: 500;
        }
        .loading-status {
            color: var(--text-light);
            font-size: 0.9rem;
            margin-top: 0.25rem;
        }
        .log-container {
            margin-top: 1.5rem;
            text-align: left;
            border-top: 1px solid var(--border);
            padding-top: 1rem;
        }
        .log-header {
            font-size: 0.9rem;
            font-weight: 600;
            color: var(--text);
            margin-bottom: 0.5rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        .log-messages {
            max-height: 200px;
            overflow-y: auto;
            background: var(--light);
            border-radius: var(--radius-sm);
            padding: 0.75rem;
            font-family: 'Courier New', monospace;
            font-size: 0.8rem;
            border: 1px solid var(--border);
        }
        .log-messages div {
            padding: 0.25rem 0;
            border-bottom: 1px dashed var(--border);
            color: var(--text);
            word-break: break-word;
        }
        .log-messages div:last-child {
            border-bottom: none;
        }
        .log-messages .success { color: var(--success); }
        .log-messages .error { color: var(--danger); }
        .log-messages .info { color: var(--primary); }
        .log-messages .warning { color: var(--warning); }
        .btn-delete.loading {
            opacity: 0.7;
            pointer-events: none;
            position: relative;
        }
        .btn-delete.loading::after {
            content: '';
            position: absolute;
            width: 16px;
            height: 16px;
            top: 50%;
            right: 10px;
            transform: translateY(-50%);
            border: 2px solid var(--danger-light);
            border-top: 2px solid var(--danger);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }

        .students-section {
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }
        .students-section .form-group {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }
        .students-section .form-group label {
            font-weight: 600;
            color: var(--dark);
        }
        .students-section .form-group input {
            padding: 0.75rem 1rem;
            border: 2px solid var(--border);
            border-radius: var(--radius);
            font-size: 1rem;
            transition: all 0.2s;
        }
        .students-section .form-group input:focus {
            outline: none;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
        }
        #generationResult {
            margin-top: 1.5rem;
            padding-top: 1rem;
            border-top: 1px solid var(--border);
        }
        #generationResult h4 {
            margin-bottom: 1rem;
            color: var(--dark);
        }
        #studentsTable {
            font-size: 0.85rem;
        }
        #studentsTable td {
            font-family: monospace;
            font-size: 0.8rem;
        }
        .btn-generate {
            background: linear-gradient(135deg, var(--success) 0%, #059669 100%);
        }
        .btn-generate:hover {
            background: linear-gradient(135deg, #059669 0%, #047857 100%);
        }
    </style>
</head>
<body>
    <div class="loading-overlay" id="loadingOverlay">
        <div class="loading-spinner">
            <div class="spinner"></div>
            <div class="progress-bar">
                <div class="progress-fill" id="progressFill"></div>
            </div>
            <div class="loading-text" id="loadingText">Загрузка базы данных...</div>
            <div class="loading-status" id="loadingStatus">Подготовка...</div>
            <div class="log-container" id="logContainer">
                <div class="log-header">
                    <span>📋 Детальный лог выполнения:</span>
                    <span style="font-size: 0.7rem; color: var(--text-light);" id="queryCounter">0/0 запросов</span>
                </div>
                <div class="log-messages" id="logMessages"></div>
            </div>
        </div>
    </div>

    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Панель преподавателя</span>
            </div>
            <div class="nav-right">
                <a href="index.jsp" class="nav-link">Тренажёр</a>
                <a href="teacher.jsp" class="nav-link active">Панель преподавателя</a>
                <a href="manageStudents.jsp" class="nav-link">Студенты</a>
                <a href="profile.jsp" class="nav-link">Профиль</a>
                <a href="#" id="logoutBtn" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <main class="container">
        <h2 class="page-title">Панель управления</h2>

        <div class="card">
            <h3 class="card-title">📤 Загрузить новую учебную базу</h3>
            <form id="uploadForm" class="upload-form" autocomplete="off">
                <div class="form-group">
                    <label class="form-label">SQL-скрипт</label>
                    <div class="file-upload">
                        <input type="file" id="sqlFile" accept=".sql" class="file-input" onchange="updateFileLabel(this)" required>
                        <label for="sqlFile" class="file-label" id="fileLabel">
                            <span class="file-icon">📎</span>
                            <span id="fileName">Выберите SQL файл</span>
                        </label>
                    </div>
                    <div style="font-size: 0.8rem; color: var(--text-light); margin-top: 0.25rem;">Максимальный размер: 10 MB</div>
                </div>
                <div class="form-actions">
                    <button type="submit" class="btn btn-primary">Создать базу данных</button>
                </div>
            </form>
        </div>

        <div class="card">
            <h3 class="card-title">👥 Управление студентами</h3>
            <div class="students-section">
                <div class="form-group">
                    <label for="groupName">Название группы</label>
                    <input type="text" id="groupName" class="form-input" placeholder="Например: ИВТ-221, ПИ-202">
                </div>
                <div class="form-group">
                    <label for="studentCount">Количество студентов</label>
                    <input type="number" id="studentCount" class="form-input" min="1" max="100" value="5">
                </div>
                <button id="generateStudentsBtn" class="btn btn-primary btn-generate">
                    <span class="btn-icon">🎓</span>
                    Сгенерировать студентов
                </button>
            </div>
            <div id="generationResult" style="display: none; margin-top: 1.5rem;">
                <h4>📊 Сгенерированные студенты</h4>
                <div class="table-wrapper">
                    <table id="studentsTable" class="results-table">
                        <thead>
                            <tr>
                                <th>Логин</th>
                                <th>Пароль</th>
                                <th>ФИО</th>
                                <th>Email</th>
                                <th>Группа</th>
                            </tr>
                        </thead>
                        <tbody></tbody>
                    </table>
                </div>
                <button id="downloadExcelBtn" class="btn btn-secondary" style="margin-top: 1rem;">
                    <span class="btn-icon">📥</span>
                    Скачать CSV
                </button>
            </div>
        </div>

        <div class="card">
            <h3 class="card-title">🗄️ Существующие базы данных</h3>
            <div class="db-manager-list" id="databasesList"><div class="empty-state">Загрузка...</div></div>
        </div>

        <div class="card">
            <h3 class="card-title">👀 Активные сессии студентов</h3>
            <div class="sessions-list" id="sessionsList"><div class="empty-state">Загрузка...</div></div>
        </div>
    </main>

    <script>
        function getAccessToken() {
            return localStorage.getItem('accessToken');
        }

        function getUser() {
            try {
                return JSON.parse(localStorage.getItem('user') || '{}');
            } catch(e) {
                return {};
            }
        }

        function getAuthHeader() {
            const token = getAccessToken();
            return token ? { 'Authorization': 'Bearer ' + token } : {};
        }

        (function checkAuth() {
            const token = getAccessToken();
            const userData = getUser();
            if (!token || !userData || userData.role !== 'teacher') {
                window.location.href = '/login.jsp';
            }
        })();

        const logoutBtn = document.getElementById('logoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', function(e) {
                e.preventDefault();
                const token = getAccessToken();
                if (token) {
                    fetch('/api/auth/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + token }
                    }).catch(function(err) {
                        console.error('Logout error:', err);
                    });
                }
                localStorage.clear();
                window.location.href = '/login.jsp';
            });
        }

        function updateFileLabel(input) {
            var fileNameSpan = document.getElementById('fileName');
            var fileUpload = document.querySelector('.file-upload');
            if (input.files && input.files.length > 0) {
                var fileName = input.files[0].name;
                var fileSize = (input.files[0].size / 1024).toFixed(0);
                if (fileSize > 1024) {
                    fileSize = (input.files[0].size / 1024 / 1024).toFixed(2) + ' MB';
                } else {
                    fileSize = fileSize + ' KB';
                }
                fileNameSpan.textContent = '📄 ' + fileName + ' (' + fileSize + ')';
                fileUpload.style.borderColor = 'var(--primary)';
                fileUpload.style.background = 'var(--primary-light)';
            } else {
                fileNameSpan.textContent = 'Выберите SQL файл';
                fileUpload.style.borderColor = 'var(--border)';
                fileUpload.style.background = 'var(--light)';
            }
        }

        function getDatabaseDescription(dbName) {
            var descriptions = {
                'sql_tutor_university_db': '📚 основная учебная база (студенты, курсы)',
                'db_university': '🏛️ университетская база',
                'archaeology_10m': '🏺 археология (10 млн записей)'
            };
            return descriptions[dbName] || '📁 учебная база';
        }

        function addLogMessage(message, type) {
            var logMessages = document.getElementById('logMessages');
            var logEntry = document.createElement('div');
            logEntry.className = type || 'info';
            var time = new Date().toLocaleTimeString();
            logEntry.textContent = '[' + time + '] ' + message;
            logMessages.appendChild(logEntry);
            logMessages.scrollTop = logMessages.scrollHeight;
        }

        function loadDatabases() {
            fetch('/api/teacher?action=list', { headers: getAuthHeader() })
                .then(function(response) {
                    if (response.status === 401) {
                        localStorage.clear();
                        window.location.href = '/login.jsp';
                        throw new Error('Unauthorized');
                    }
                    return response.json();
                })
                .then(function(data) {
                    var list = document.getElementById('databasesList');
                    if (data.error) {
                        list.innerHTML = '<div class="empty-state">Ошибка: ' + data.error + '</div>';
                        return;
                    }
                    if (data.databases && data.databases.length > 0) {
                        var html = '';
                        for (var i = 0; i < data.databases.length; i++) {
                            var db = data.databases[i];
                            html += '<div class="db-manager-item">' +
                                        '<div class="db-info">' +
                                            '<span class="db-name">' + db + '</span>' +
                                            '<span class="db-meta">' + getDatabaseDescription(db) + '</span>' +
                                        '</div>' +
                                        '<button class="btn-delete" onclick="deleteDatabase(\'' + db + '\')">Удалить</button>' +
                                    '</div>';
                        }
                        list.innerHTML = html;
                    } else {
                        list.innerHTML = '<div class="empty-state">Нет баз данных</div>';
                    }
                })
                .catch(function(error) {
                    console.error("Fetch error:", error);
                    document.getElementById('databasesList').innerHTML = '<div class="empty-state">Ошибка загрузки</div>';
                });
        }

        function deleteDatabase(dbName) {
            if (dbName === 'sql_tutor_university_db') {
                if (!confirm('⚠️ Это основная база проекта. Вы уверены, что хотите её удалить?')) return;
            }
            if (confirm('Удалить базу данных "' + dbName + '"?')) {
                var overlay = document.getElementById('loadingOverlay');
                var progressFill = document.getElementById('progressFill');
                overlay.style.display = 'flex';
                progressFill.style.width = '30%';
                fetch('/api/teacher', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded', ...getAuthHeader() },
                    body: new URLSearchParams({ 'action': 'delete', 'dbName': dbName })
                })
                .then(function(response) { progressFill.style.width = '70%'; return response.json(); })
                .then(function(data) {
                    progressFill.style.width = '100%';
                    setTimeout(function() {
                        overlay.style.display = 'none';
                        if (data.success) loadDatabases();
                        else alert('Ошибка при удалении: ' + (data.error || 'неизвестная ошибка'));
                    }, 500);
                })
                .catch(function(error) { overlay.style.display = 'none'; alert('Ошибка соединения: ' + error); });
            }
        }

        function loadSessions() {
            fetch('/api/teacher?action=sessions', { headers: getAuthHeader() })
                .then(function(response) {
                    if (response.status === 401) {
                        localStorage.clear();
                        window.location.href = '/login.jsp';
                        throw new Error('Unauthorized');
                    }
                    return response.json();
                })
                .then(function(data) {
                    var list = document.getElementById('sessionsList');
                    if (data.sessions && data.sessions.length > 0) {
                        var html = '<table class="results-table">';
                        html += '<thead>';
                        html += '<tr>';
                        html += '<th>Сессия</th>';
                        html += '<th>Последний запрос</th>';
                        html += '<th>Время</th>';
                        html += '</tr>';
                        html += '</thead>';
                        html += '<tbody>';

                        for (var i = 0; i < data.sessions.length; i++) {
                            var s = data.sessions[i];
                            var time = new Date(s.lastAccess).toLocaleTimeString();
                            var shortQuery = s.lastQuery.length > 50 ? s.lastQuery.substring(0, 50) + '...' : s.lastQuery;
                            html += '<tr>';
                            html += '<td>' + s.sessionId.substring(0, 8) + '...</td>';
                            html += '<td title="' + s.lastQuery.replace(/"/g, '&quot;') + '">' + shortQuery + '</td>';
                            html += '<td>' + time + '</td>';
                            html += '</tr>';
                        }
                        html += '</tbody>';
                        html += '</table>';
                        list.innerHTML = html;
                    } else {
                        list.innerHTML = '<div class="empty-state">Нет активных сессий</div>';
                    }
                })
                .catch(function(error) {
                    console.error("Sessions fetch error:", error);
                    document.getElementById('sessionsList').innerHTML = '<div class="empty-state">Ошибка загрузки сессий</div>';
                });
        }

        const generateBtn = document.getElementById('generateStudentsBtn');
        const groupNameInput = document.getElementById('groupName');
        const studentCountInput = document.getElementById('studentCount');
        const generationResult = document.getElementById('generationResult');
        const studentsTableBody = document.querySelector('#studentsTable tbody');
        const downloadExcelBtn = document.getElementById('downloadExcelBtn');
        window.generatedStudents = [];

        if (generateBtn) {
            generateBtn.addEventListener('click', async () => {
                const groupName = groupNameInput.value.trim();
                const count = parseInt(studentCountInput.value);

                if (!groupName) {
                    alert('Введите название группы');
                    return;
                }

                if (isNaN(count) || count < 1 || count > 100) {
                    alert('Количество студентов должно быть от 1 до 100');
                    return;
                }

                generateBtn.disabled = true;
                generateBtn.textContent = '⏳ Генерация...';

                try {
                    const response = await fetch('/api/teacher/students?action=generate', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                            'Authorization': 'Bearer ' + getAccessToken()
                        },
                        body: new URLSearchParams({
                            groupName: groupName,
                            count: count
                        })
                    });

                    const data = await response.json();

                    if (data.success && data.students && data.students.length > 0) {
                        studentsTableBody.innerHTML = '';
                        window.generatedStudents = data.students;

                        for (let i = 0; i < data.students.length; i++) {
                            const student = data.students[i];
                            const row = studentsTableBody.insertRow();
                            row.insertCell(0).textContent = student.login;
                            row.insertCell(1).textContent = student.password;
                            row.insertCell(2).textContent = student.fullName;
                            row.insertCell(3).textContent = student.email;
                            row.insertCell(4).textContent = student.groupName;
                        }

                        generationResult.style.display = 'block';
                        alert('✅ Успешно сгенерировано ' + data.students.length + ' студентов!');
                    } else {
                        alert('Ошибка: ' + (data.error || 'Не удалось сгенерировать студентов'));
                    }
                } catch (error) {
                    console.error('Generation error:', error);
                    alert('Ошибка соединения с сервером');
                } finally {
                    generateBtn.disabled = false;
                    generateBtn.textContent = '🎓 Сгенерировать студентов';
                }
            });
        }

        if (downloadExcelBtn) {
            downloadExcelBtn.addEventListener('click', () => {
                if (!window.generatedStudents || window.generatedStudents.length === 0) {
                    alert('Нет данных для скачивания. Сначала сгенерируйте студентов.');
                    return;
                }

                let csv = '\uFEFF';
                csv += 'Логин;Пароль;ФИО;Email;Группа\n';

                for (let i = 0; i < window.generatedStudents.length; i++) {
                    const s = window.generatedStudents[i];
                    csv += '"' + (s.login || '') + '";';
                    csv += '"' + (s.password || '') + '";';
                    csv += '"' + (s.fullName || '') + '";';
                    csv += '"' + (s.email || '') + '";';
                    csv += '"' + (s.groupName || '') + '"\n';
                }

                const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
                const link = document.createElement('a');
                const url = URL.createObjectURL(blob);
                const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
                const groupNameForFile = (window.generatedStudents[0].groupName || 'students').replace(/[^a-zA-Z0-9а-яА-Я]/g, '_');

                link.setAttribute('href', url);
                link.setAttribute('download', 'students_' + groupNameForFile + '_' + timestamp + '.csv');
                link.style.visibility = 'hidden';
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(url);
            });
        }

        document.getElementById('uploadForm').addEventListener('submit', function(e) {
            e.preventDefault();
            var fileInput = document.getElementById('sqlFile');
            var file = fileInput.files[0];
            if (!file) { alert('Выберите SQL файл'); return; }

            var overlay = document.getElementById('loadingOverlay');
            var progressFill = document.getElementById('progressFill');
            var loadingText = document.getElementById('loadingText');
            var loadingStatus = document.getElementById('loadingStatus');
            var logMessages = document.getElementById('logMessages');

            overlay.style.display = 'flex';
            progressFill.style.width = '10%';
            loadingText.textContent = 'Загрузка базы данных...';
            loadingStatus.textContent = 'Отправка файла...';
            logMessages.innerHTML = '';
            addLogMessage('Начало загрузки файла: ' + file.name, 'info');

            var formData = new FormData();
            formData.append('action', 'upload');
            formData.append('sqlFile', file);

            var eventSource = new EventSource('/api/logs');
            eventSource.onmessage = function(event) {
                try {
                    var data = JSON.parse(event.data);
                    if (data.type === 'start') addLogMessage('Начало выполнения скрипта. Всего запросов: ' + data.total, 'info');
                    else if (data.type === 'progress') {
                        var percent = Math.round((data.current / data.total) * 100);
                        progressFill.style.width = percent + '%';
                        loadingStatus.textContent = 'Выполнение: ' + percent + '% (' + data.current + '/' + data.total + ')';
                        addLogMessage('▶ ' + data.query, 'info');
                    } else if (data.type === 'success') addLogMessage('✅ ' + data.message, 'success');
                    else if (data.type === 'error') addLogMessage('❌ Ошибка: ' + data.error, 'error');
                    else if (data.type === 'complete') { addLogMessage('🎉 ' + data.message, 'success'); setTimeout(function() { eventSource.close(); }, 2000); }
                } catch (e) { addLogMessage(event.data, 'info'); }
            };
            eventSource.onerror = function() { addLogMessage('⚠️ Соединение с сервером логов закрыто', 'warning'); eventSource.close(); };

            var xhr = new XMLHttpRequest();
            xhr.upload.addEventListener('progress', function(e) {
                if (e.lengthComputable) {
                    var percent = (e.loaded / e.total) * 100;
                    progressFill.style.width = percent + '%';
                    loadingStatus.textContent = 'Загрузка файла: ' + Math.round(percent) + '%';
                }
            });
            xhr.addEventListener('load', function() {
                if (xhr.status === 200) {
                    try {
                        var data = JSON.parse(xhr.responseText);
                        if (data.success) {
                            progressFill.style.width = '100%';
                            loadingText.textContent = 'Готово!';
                            loadingStatus.textContent = 'База данных успешно создана';
                            addLogMessage('✅ База данных успешно создана!', 'success');
                            setTimeout(function() {
                                overlay.style.display = 'none';
                                document.getElementById('uploadForm').reset();
                                document.getElementById('fileName').textContent = 'Выберите SQL файл';
                                loadDatabases();
                            }, 2000);
                        } else {
                            overlay.style.display = 'none';
                            addLogMessage('❌ Ошибка: ' + data.error, 'error');
                            alert('Ошибка: ' + data.error);
                        }
                    } catch (e) { overlay.style.display = 'none'; alert('Ошибка при обработке ответа сервера'); }
                } else { overlay.style.display = 'none'; alert('Ошибка соединения: ' + xhr.status); }
            });
            xhr.addEventListener('error', function() { overlay.style.display = 'none'; alert('Ошибка соединения'); });
            xhr.open('POST', '/api/teacher', true);
            xhr.setRequestHeader('Authorization', 'Bearer ' + getAccessToken());
            xhr.send(formData);
        });

        loadDatabases();
        loadSessions();
        setInterval(loadSessions, 5000);
    </script>
</body>
</html>