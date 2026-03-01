<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    // Проверка авторизации преподавателя
    if (session == null || !"teacher".equals(session.getAttribute("role"))) {
        response.sendRedirect("teacher-login.jsp");
        return;
    }
%>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Tutor - Панель преподавателя</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <!-- Навигация -->
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Tutor</h1>
                <span class="badge">Панель преподавателя</span>
            </div>
            <div class="nav-right">
                <a href="index.jsp" class="nav-link">Студент</a>
                <a href="teacher.jsp" class="nav-link active">Преподаватель</a>
                <a href="logout" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <main class="container">
        <h2 class="page-title">Панель управления базами данных</h2>

        <!-- Форма загрузки новой базы -->
        <div class="card">
            <h3 class="card-title">Загрузить новую учебную базу</h3>

            <form id="uploadForm" class="upload-form">
                <div class="form-group">
                    <label class="form-label">Название базы данных</label>
                    <input type="text" name="dbName" class="form-input" placeholder="Например: Туристическое агентство" required>
                </div>

                <div class="form-group">
                    <label class="form-label">Описание</label>
                    <textarea name="description" class="form-textarea" rows="2" placeholder="Краткое описание и список таблиц"></textarea>
                </div>

                <div class="form-group">
                    <label class="form-label">SQL-скрипт</label>
                    <div class="file-upload">
                        <input type="file" id="sqlFile" accept=".sql" class="file-input">
                        <label for="sqlFile" class="file-label">
                            <span class="file-icon">📎</span>
                            <span>Выберите файл или перетащите</span>
                        </label>
                    </div>
                </div>

                <div class="form-actions">
                    <button type="submit" class="btn btn-primary">
                        Создать базу данных
                    </button>
                </div>
            </form>
        </div>

        <!-- Список существующих баз -->
        <div class="card">
            <h3 class="card-title">Существующие базы данных</h3>

            <div class="db-manager-list" id="databasesList">
                <div class="empty-state">Загрузка...</div>
            </div>
        </div>

        <!-- Блок мониторинга сессий -->
        <div class="card">
            <h3 class="card-title">Активные сессии студентов</h3>
            <div class="sessions-list" id="sessionsList">
                <div class="empty-state">Загрузка...</div>
            </div>
        </div>
    </main>

    <script>
        // Загрузка списка баз
        function loadDatabases() {
            fetch('/api/teacher?action=list')
                .then(response => response.json())
                .then(data => {
                    const list = document.getElementById('databasesList');
                    if (data.databases && data.databases.length > 0) {
                        let html = '';
                        data.databases.forEach(db => {
                            html += `
                                <div class="db-manager-item">
                                    <div class="db-info">
                                        <span class="db-name">${db}</span>
                                        <span class="db-meta">учебная база</span>
                                    </div>
                                    <button class="btn-delete" onclick="deleteDatabase('${db}')">Удалить</button>
                                </div>
                            `;
                        });
                        list.innerHTML = html;
                    } else {
                        list.innerHTML = '<div class="empty-state">Нет баз данных</div>';
                    }
                });
        }

        // Удаление базы
        function deleteDatabase(dbName) {
            if (confirm(`Удалить базу данных "${dbName}"?`)) {
                fetch('/api/teacher', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: new URLSearchParams({
                        'action': 'delete',
                        'dbName': dbName
                    })
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        loadDatabases();
                    } else {
                        alert('Ошибка при удалении');
                    }
                });
            }
        }

        // Загрузка активных сессий
        function loadSessions() {
            fetch('/api/teacher?action=sessions')
                .then(response => response.json())
                .then(data => {
                    const list = document.getElementById('sessionsList');
                    if (data.sessions && data.sessions.length > 0) {
                        let html = '<table><tr><th>Сессия</th><th>Последний запрос</th><th>Время</th></tr>';
                        data.sessions.forEach(s => {
                            const time = new Date(s.lastAccess).toLocaleTimeString();
                            const shortQuery = s.lastQuery.length > 30
                                ? s.lastQuery.substring(0,30) + '...'
                                : s.lastQuery;
                            html += `<tr>
                                <td>${s.sessionId.substring(0,8)}...</td>
                                <td>${shortQuery}</td>
                                <td>${time}</td>
                            </tr>`;
                        });
                        html += '</table>';
                        list.innerHTML = html;
                    } else {
                        list.innerHTML = '<div class="empty-state">Нет активных сессий</div>';
                    }
                });
        }

        // Обработка формы загрузки
        document.getElementById('uploadForm').addEventListener('submit', function(e) {
            e.preventDefault();

            const formData = new FormData();
            formData.append('action', 'upload');
            formData.append('dbName', document.querySelector('input[name="dbName"]').value);
            formData.append('sqlFile', document.getElementById('sqlFile').files[0]);

            fetch('/api/teacher', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    alert('База данных успешно создана');
                    document.getElementById('uploadForm').reset();
                    loadDatabases();
                } else {
                    alert('Ошибка: ' + data.error);
                }
            });
        });

        // Загружаем данные при открытии страницы
        loadDatabases();
        loadSessions();

        // Обновляем сессии каждые 5 секунд
        setInterval(loadSessions, 5000);
    </script>
</body>
</html>