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
    <style>
        /* Индикатор загрузки */
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

        /* Лог выполнения */
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

        .log-messages .success {
            color: var(--success);
        }

        .log-messages .error {
            color: var(--danger);
        }

        .log-messages .info {
            color: var(--primary);
        }

        .log-messages .warning {
            color: var(--warning);
        }

        /* Анимация для кнопки удаления */
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
    </style>
</head>
<body>
    <!-- Индикатор загрузки -->
    <div class="loading-overlay" id="loadingOverlay">
        <div class="loading-spinner">
            <div class="spinner"></div>
            <div class="progress-bar">
                <div class="progress-fill" id="progressFill"></div>
            </div>
            <div class="loading-text" id="loadingText">Загрузка базы данных...</div>
            <div class="loading-status" id="loadingStatus">Подготовка...</div>

            <!-- Контейнер для лога выполнения -->
            <div class="log-container" id="logContainer">
                <div class="log-header">
                    <span>📋 Детальный лог выполнения:</span>
                    <span style="font-size: 0.7rem; color: var(--text-light);" id="queryCounter">0/0 запросов</span>
                </div>
                <div class="log-messages" id="logMessages"></div>
            </div>
        </div>
    </div>

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
                    <label class="form-label">SQL-скрипт</label>
                    <div class="file-upload">
                        <input type="file" id="sqlFile" accept=".sql" class="file-input" onchange="updateFileLabel(this)" required>
                        <label for="sqlFile" class="file-label" id="fileLabel">
                            <span class="file-icon">📎</span>
                            <span id="fileName">Выберите SQL файл</span>
                        </label>
                    </div>
                    <div style="font-size: 0.8rem; color: var(--text-light); margin-top: 0.25rem;">
                        Максимальный размер: 10 MB
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
        // Функция для обновления метки при выборе файла
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

                // Визуальная обратная связь
                fileUpload.style.borderColor = 'var(--primary)';
                fileUpload.style.background = 'var(--primary-light)';
            } else {
                fileNameSpan.textContent = 'Выберите SQL файл';
                fileUpload.style.borderColor = 'var(--border)';
                fileUpload.style.background = 'var(--light)';
            }
        }

        // Функция для получения описания базы данных
        function getDatabaseDescription(dbName) {
            var descriptions = {
                'sql_tutor_university_db': '📚 основная учебная база (студенты, курсы)',
                'db_university': '🏛️ университетская база',
                'Столовая': '🍽️ тестовая база данных',
                'archaeology_10m': '🏺 археология (10 млн записей)'
            };
            return descriptions[dbName] || '📁 учебная база';
        }

        // Функция для добавления сообщения в лог
        function addLogMessage(message, type) {
            var logMessages = document.getElementById('logMessages');
            var logEntry = document.createElement('div');
            logEntry.className = type || 'info';

            var time = new Date().toLocaleTimeString();
            logEntry.textContent = '[' + time + '] ' + message;

            logMessages.appendChild(logEntry);
            logMessages.scrollTop = logMessages.scrollHeight;
        }

        // Загрузка списка баз
        function loadDatabases() {
            console.log("=== Teacher page: Loading databases ===");

            fetch('/api/teacher?action=list')
                .then(function(response) {
                    console.log("Response status:", response.status);
                    return response.json();
                })
                .then(function(data) {
                    console.log("Data received from server:", data);

                    var list = document.getElementById('databasesList');

                    if (data.error) {
                        console.error("Server returned error:", data.error);
                        list.innerHTML = '<div class="empty-state">Ошибка: ' + data.error + '</div>';
                        return;
                    }

                    if (data.databases && data.databases.length > 0) {
                        console.log("Found " + data.databases.length + " databases:", data.databases);

                        var html = '';
                        for (var i = 0; i < data.databases.length; i++) {
                            var db = data.databases[i];
                            console.log("Adding database to list:", db);
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
                        console.log("No databases found");
                        list.innerHTML = '<div class="empty-state">Нет баз данных</div>';
                    }
                })
                .catch(function(error) {
                    console.error("Fetch error:", error);
                    document.getElementById('databasesList').innerHTML =
                        '<div class="empty-state">Ошибка загрузки</div>';
                });
        }

        // Удаление базы с индикатором
        function deleteDatabase(dbName) {
            console.log("Attempting to delete database:", dbName);

            // Защита от удаления важных баз
            if (dbName === 'sql_tutor_university_db') {
                if (!confirm('⚠️ Это основная база проекта. Вы уверены, что хотите её удалить?')) {
                    return;
                }
            }

            if (confirm('Удалить базу данных "' + dbName + '"?')) {
                console.log("User confirmed deletion of:", dbName);

                // Показываем индикатор
                var overlay = document.getElementById('loadingOverlay');
                var progressFill = document.getElementById('progressFill');
                var loadingText = document.getElementById('loadingText');
                var loadingStatus = document.getElementById('loadingStatus');

                overlay.style.display = 'flex';
                progressFill.style.width = '30%';
                loadingText.textContent = 'Удаление базы данных...';
                loadingStatus.textContent = 'Завершение соединений...';

                // Добавляем класс loading к кнопке
                var buttons = document.querySelectorAll('.btn-delete');
                buttons.forEach(function(btn) {
                    if (btn.getAttribute('onclick')?.includes(dbName)) {
                        btn.classList.add('loading');
                    }
                });

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
                .then(function(response) {
                    progressFill.style.width = '70%';
                    loadingStatus.textContent = 'Удаление...';
                    return response.json();
                })
                .then(function(data) {
                    console.log("Delete response:", data);
                    progressFill.style.width = '100%';
                    loadingStatus.textContent = 'Готово!';

                    setTimeout(function() {
                        overlay.style.display = 'none';

                        // Убираем класс loading
                        buttons.forEach(function(btn) {
                            btn.classList.remove('loading');
                        });

                        if (data.success) {
                            loadDatabases();
                        } else {
                            alert('Ошибка при удалении: ' + (data.error || 'неизвестная ошибка'));
                        }
                    }, 500);
                })
                .catch(function(error) {
                    overlay.style.display = 'none';
                    buttons.forEach(function(btn) {
                        btn.classList.remove('loading');
                    });
                    console.error("Delete fetch error:", error);
                    alert('Ошибка соединения: ' + error);
                });
            }
        }

        // Загрузка активных сессий
        function loadSessions() {
            fetch('/api/teacher?action=sessions')
                .then(function(response) {
                    return response.json();
                })
                .then(function(data) {
                    console.log("Sessions data:", data);

                    var list = document.getElementById('sessionsList');
                    if (data.sessions && data.sessions.length > 0) {
                        var html = '<table><tr><th>Сессия</th><th>Последний запрос</th><th>Время</th></tr>';
                        for (var i = 0; i < data.sessions.length; i++) {
                            var s = data.sessions[i];
                            var time = new Date(s.lastAccess).toLocaleTimeString();
                            var shortQuery = s.lastQuery.length > 30
                                ? s.lastQuery.substring(0,30) + '...'
                                : s.lastQuery;
                            html += '<tr>' +
                                        '<td>' + s.sessionId.substring(0,8) + '...</td>' +
                                        '<td>' + shortQuery + '</td>' +
                                        '<td>' + time + '</td>' +
                                    '</tr>';
                        }
                        html += '</table>';
                        list.innerHTML = html;
                    } else {
                        list.innerHTML = '<div class="empty-state">Нет активных сессий</div>';
                    }
                })
                .catch(function(error) {
                    console.error("Sessions fetch error:", error);
                });
        }

        // Обработка формы загрузки с индикатором прогресса и детальным логом
        document.getElementById('uploadForm').addEventListener('submit', function(e) {
            e.preventDefault();

            var fileInput = document.getElementById('sqlFile');
            var file = fileInput.files[0];

            console.log("Upload form submitted");
            console.log("File selected:", file?.name);

            if (!file) {
                alert('Выберите SQL файл');
                return;
            }

            // Показываем индикатор загрузки
            var overlay = document.getElementById('loadingOverlay');
            var progressFill = document.getElementById('progressFill');
            var loadingText = document.getElementById('loadingText');
            var loadingStatus = document.getElementById('loadingStatus');
            var logMessages = document.getElementById('logMessages');

            overlay.style.display = 'flex';
            progressFill.style.width = '10%';
            loadingText.textContent = 'Загрузка базы данных...';
            loadingStatus.textContent = 'Отправка файла...';

            // Очищаем лог
            logMessages.innerHTML = '';
            addLogMessage('Начало загрузки файла: ' + file.name, 'info');

            var formData = new FormData();
            formData.append('action', 'upload');
            formData.append('sqlFile', file);

            var eventSource = new EventSource('/api/logs');

            eventSource.onmessage = function(event) {
                try {
                    var data = JSON.parse(event.data);

                    if (data.type === 'start') {
                        addLogMessage('Начало выполнения скрипта. Всего запросов: ' + data.total, 'info');
                    }
                    else if (data.type === 'progress') {
                        var percent = Math.round((data.current / data.total) * 100);
                        progressFill.style.width = percent + '%';
                        loadingStatus.textContent = 'Выполнение: ' + percent + '% (' + data.current + '/' + data.total + ')';
                        addLogMessage('▶ ' + data.query, 'info');
                    }
                    else if (data.type === 'success') {
                        addLogMessage('✅ ' + data.message, 'success');
                    }
                    else if (data.type === 'error') {
                        addLogMessage('❌ Ошибка: ' + data.error, 'error');
                    }
                    else if (data.type === 'complete') {
                        addLogMessage('🎉 ' + data.message, 'success');
                        // Закрываем соединение через 2 секунды
                        setTimeout(function() {
                            eventSource.close();
                        }, 2000);
                    }
                } catch (e) {
                    // Если не JSON, показываем как текст
                    addLogMessage(event.data, 'info');
                }
            };

            eventSource.onerror = function() {
                addLogMessage('⚠️ Соединение с сервером логов закрыто', 'warning');
                eventSource.close();
            };

            // Создаем XMLHttpRequest для отслеживания прогресса
            var xhr = new XMLHttpRequest();

            xhr.upload.addEventListener('progress', function(e) {
                if (e.lengthComputable) {
                    var percent = (e.loaded / e.total) * 100;
                    progressFill.style.width = percent + '%';
                    loadingStatus.textContent = 'Загрузка файла: ' + Math.round(percent) + '%';

                    if (Math.round(percent) % 25 === 0) {
                        addLogMessage('Загружено ' + Math.round(percent) + '% файла', 'info');
                    }
                }
            });

            xhr.addEventListener('load', function() {
                if (xhr.status === 200) {
                    try {
                        var data = JSON.parse(xhr.responseText);
                        progressFill.style.width = '100%';
                        loadingText.textContent = 'Создание базы данных...';
                        loadingStatus.textContent = 'Выполнение SQL скрипта...';

                        addLogMessage('Файл загружен, начинается выполнение SQL скрипта...', 'success');

                        if (data.success) {
                            progressFill.style.width = '100%';
                            loadingText.textContent = 'Готово!';
                            loadingStatus.textContent = 'База данных успешно создана';
                            addLogMessage('✅ База данных успешно создана!', 'success');

                            // Закрываем SSE соединение
                            if (eventSource) {
                                eventSource.close();
                            }

                            setTimeout(function() {
                                overlay.style.display = 'none';

                                // Сброс формы и визуального состояния
                                document.getElementById('uploadForm').reset();
                                document.getElementById('fileName').textContent = 'Выберите SQL файл';
                                var fileUpload = document.querySelector('.file-upload');
                                fileUpload.style.borderColor = 'var(--border)';
                                fileUpload.style.background = 'var(--light)';

                                loadDatabases();
                            }, 2000);
                        } else {
                            overlay.style.display = 'none';
                            addLogMessage('❌ Ошибка: ' + data.error, 'error');
                            alert('Ошибка: ' + data.error);

                            // Закрываем SSE соединение при ошибке
                            if (eventSource) {
                                eventSource.close();
                            }
                        }

                    } catch (e) {
                        overlay.style.display = 'none';
                        console.error("Parse error:", e);
                        alert('Ошибка при обработке ответа сервера');
                    }
                } else {
                    overlay.style.display = 'none';
                    addLogMessage('❌ Ошибка соединения: ' + xhr.status, 'error');
                    alert('Ошибка соединения: ' + xhr.status);
                }
            });

            xhr.addEventListener('error', function() {
                overlay.style.display = 'none';
                addLogMessage('❌ Ошибка соединения', 'error');
                alert('Ошибка соединения');
            });

            xhr.open('POST', '/api/teacher', true);
            xhr.send(formData);
        });

        // Загружаем данные при открытии страницы
        console.log("Teacher page loaded, loading databases...");
        loadDatabases();
        loadSessions();

        // Обновляем сессии каждые 5 секунд
        setInterval(loadSessions, 5000);
    </script>
</body>
</html>