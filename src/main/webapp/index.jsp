<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Tutor - учебная платформа</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <!-- Навигация -->
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Tutor</h1>
                <span class="badge">Учебная песочница</span>
            </div>
            <div class="nav-right">
                <a href="index.jsp" class="nav-link active">Студент</a>
                <a href="teacher.jsp" class="nav-link">Преподаватель</a>

                <!-- Кнопка выхода для преподавателя -->
                <% if (session != null && "teacher".equals(session.getAttribute("role"))) { %>
                    <a href="logout" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
                <% } %>
            </div>
        </div>
    </nav>

    <!-- Основной контент -->
    <main class="container">
        <!-- Заголовок -->
        <div class="page-header">
            <h2 class="page-title">Выполнение SQL-запросов</h2>
            <p class="page-description">База данных: <strong>sql_tutor_university_db</strong></p>
        </div>

        <div class="two-columns">
            <!-- Левая колонка: информация о базе -->
            <div class="left-column">
                <div class="card">
                    <h3 class="card-title">
                        <span class="icon">📁</span>
                        Текущая база данных
                    </h3>

                    <div class="db-info-card">
                        <div class="db-header">
                            <span class="db-icon">🗄️</span>
                            <span class="db-name" id="dbName">sql_tutor_university_db</span>
                        </div>

                        <div class="db-tables-section">
                            <div class="tables-header">
                                <span class="tables-icon">📋</span>
                                <h4>Таблицы в базе</h4>
                            </div>
                            <div class="tables-grid" id="tablesList">
                                <!-- Таблицы будут загружены динамически -->
                                <li>Загрузка...</li>
                            </div>
                        </div>

                        <!-- Статус подключения -->
                        <div class="connection-status">
                            <span class="status-indicator online"></span>
                            <span class="status-text">PostgreSQL: подключено</span>
                            <span class="db-badge">sql_tutor_university_db</span>
                        </div>
                    </div>
                </div>
            </div>

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
                                Выполнить
                            </button>
                            <button id="clearBtn" class="btn btn-secondary">
                                Очистить
                            </button>
                            <span id="executionTime" class="execution-time"></span>
                        </div>
                    </div>

                    <!-- Результаты запроса -->
                    <div class="results-section">
                        <div class="results-header">
                            <h4 class="results-title">Результат:</h4>
                            <span id="rowCount" class="row-count"></span>
                        </div>

                        <div id="resultContainer" class="results-table">
                            <div class="empty-state">
                                Нажмите "Выполнить", чтобы увидеть результат
                            </div>
                        </div>
                    </div>

                    <!-- План выполнения (EXPLAIN) -->
                    <div class="explain-section">
                        <details class="explain-details">
                            <summary class="explain-summary">Показать план выполнения (EXPLAIN)</summary>
                            <pre id="explainContainer" class="explain-output">-- Здесь появится вывод EXPLAIN ANALYZE</pre>
                        </details>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <script src="script.js"></script>
</body>
</html>