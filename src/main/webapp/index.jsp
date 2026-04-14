<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Trainer - учебная платформа</title>
    <link rel="stylesheet" href="style.css">

    <!-- CodeMirror CSS -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/theme/dracula.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/show-hint.min.css">
</head>
<body>
    <!-- Навигация -->
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Учебная песочница</span>
            </div>
            <div class="nav-right">
                <a href="index.jsp" class="nav-link active">Студент</a>
                <a href="teacher.jsp" id="teacherLink" class="nav-link" style="display: none;">Преподаватель</a>
                <a href="#" id="logoutBtn" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <!-- Основной контент -->
    <main class="container">
        <div class="page-header">
            <h2 class="page-title">Выполнение SQL-запросов</h2>
            <p class="page-description">Выберите базу данных и напишите запрос</p>
        </div>

        <div class="two-columns">
            <!-- Левая колонка: информация о базе + подсказки -->
            <div class="left-column">
                <div class="card">
                    <h3 class="card-title">
                        <span class="icon">📁</span>
                        Выберите базу данных
                    </h3>

                    <div class="db-selector">
                        <select id="dbSelector" class="db-select" onchange="changeDatabase(this.value)">
                            <option value="">Загрузка баз...</option>
                        </select>
                    </div>

                    <div class="db-info-card" id="dbInfoCard" style="display: none;">
                        <div class="db-header">
                            <span class="db-icon">🗄️</span>
                            <span class="db-name" id="dbName"></span>
                        </div>

                        <div class="db-tables-section">
                            <div class="tables-header">
                                <span class="tables-icon">📋</span>
                                <h4>Таблицы в базе</h4>
                            </div>
                            <div class="tables-grid" id="tablesList">
                                <li>Загрузка...</li>
                            </div>
                        </div>

                        <div class="connection-status">
                            <span class="status-indicator online"></span>
                            <span class="status-text">PostgreSQL: подключено</span>
                            <span class="db-badge" id="currentDbBadge"></span>
                        </div>
                    </div>
                </div>

                <!-- Блок подсказок -->
                <div class="card hints-card">
                    <h3 class="card-title">
                        <span class="icon">⌨️</span>
                        Горячие клавиши
                    </h3>

                    <div class="hints-container">
                        <details class="hint-details">
                            <summary class="hint-summary">
                                <span class="summary-icon">▶</span>
                                Показать горячие клавиши
                            </summary>
                            <div class="hint-content">
                                <div class="hotkeys-grid">
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Ctrl + Enter</span>
                                        <span class="hotkey-desc">Выполнить запрос</span>
                                    </div>
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Ctrl + Space</span>
                                        <span class="hotkey-desc">Автодополнение (таблицы, колонки)</span>
                                    </div>
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Tab</span>
                                        <span class="hotkey-desc">Отступ (4 пробела)</span>
                                    </div>
                                </div>
                            </div>
                        </details>
                    </div>
                </div>
            </div>  <!-- Закрывающий тег left-column -->

            <!-- Правая колонка: редактор SQL -->
            <div class="right-column">
                <div class="card">
                    <h3 class="card-title">
                        <span class="icon">✏️</span>
                        SQL запрос
                    </h3>

                    <div class="editor-container">
                        <textarea id="sqlQuery" class="sql-editor" placeholder="Введите SQL запрос...">SELECT * FROM student LIMIT 10;</textarea>

                        <div class="editor-toolbar">
                            <button id="executeBtn" class="btn btn-primary">
                                <span class="btn-icon">▶</span>
                                Выполнить (Ctrl+Enter)
                            </button>
                            <button id="clearBtn" class="btn btn-secondary">
                                <span class="btn-icon">🗑️</span>
                                Очистить
                            </button>
                            <button id="formatBtn" class="btn btn-secondary" title="Форматировать SQL (Ctrl+Shift+F)">
                                <span class="btn-icon">✨</span>
                                Формат
                            </button>
                            <button id="downloadBtn" class="btn btn-secondary" style="margin-left: auto;">
                                <span class="btn-icon">📥</span>
                                Скачать CSV
                            </button>
                            <span id="executionTime" class="execution-time"></span>
                        </div>
                    </div>

                    <div class="results-section">
                        <div class="results-header">
                            <h4 class="results-title">Результат:</h4>
                            <span id="rowCount" class="row-count"></span>
                        </div>

                        <div id="resultContainer" class="results-table">
                            <div class="empty-state">
                                Выберите базу данных и выполните запрос
                            </div>
                        </div>
                    </div>

                    <div class="explain-section">
                        <details class="explain-details">
                            <summary class="explain-summary">📊 Показать план выполнения (EXPLAIN ANALYZE)</summary>
                            <pre id="explainContainer" class="explain-output">-- Здесь появится вывод EXPLAIN ANALYZE</pre>
                        </details>
                    </div>
                </div>
            </div>
        </div>  <!-- Закрывающий тег two-columns -->
    </main>

    <!-- CodeMirror JS -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/mode/sql/sql.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/show-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/sql-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/matchbrackets.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/closebrackets.min.js"></script>

    <script src="script.js"></script>

    <script>
        // Проверка авторизации при загрузке страницы
        (function checkAuth() {
            const token = localStorage.getItem('accessToken');
            const user = localStorage.getItem('user');

            if (!token || !user) {
                window.location.href = '/login.jsp';
                return;
            }
            // Убираем проверку роли — студент или преподаватель, оба могут выполнять SQL
        })();

        // Показываем вкладку преподавателя только если роль teacher
        (function showTeacherTab() {
            const user = localStorage.getItem('user');
            if (user) {
                try {
                    const userData = JSON.parse(user);
                    if (userData.role === 'teacher') {
                        const teacherLink = document.getElementById('teacherLink');
                        if (teacherLink) {
                            teacherLink.style.display = 'inline-block';
                        }
                    }
                } catch(e) {}
            }
        })();

        // Кнопка выхода
        document.getElementById('logoutBtn')?.addEventListener('click', async (e) => {
            e.preventDefault();
            const token = localStorage.getItem('accessToken');
            if (token) {
                try {
                    await fetch('/api/auth/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                } catch(e) {}
            }
            localStorage.clear();
            window.location.href = '/login.jsp';
        });
    </script>
</body>
</html>