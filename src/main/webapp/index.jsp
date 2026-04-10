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
                <a href="teacher.jsp" class="nav-link">Преподаватель</a>

                <% if (session != null && "teacher".equals(session.getAttribute("role"))) { %>
                    <a href="logout" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
                <% } %>
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
                        <span class="icon">💡</span>
                        Подсказки
                    </h3>

                    <div class="hints-container">
                        <details class="hint-details">
                            <summary class="hint-summary">📖 Базовые команды SQL</summary>
                            <div class="hint-content">
                                <pre><code>SELECT * FROM student;                    -- все записи из таблицы
SELECT name, age FROM student;            -- только нужные колонки
SELECT * FROM student LIMIT 10;           -- первые 10 записей
SELECT * FROM student WHERE age > 18;     -- фильтрация
SELECT * FROM student ORDER BY name;      -- сортировка</code></pre>
                            </div>
                        </details>

                        <details class="hint-details">
                            <summary class="hint-summary">🔍 Фильтрация (WHERE)</summary>
                            <div class="hint-content">
                                <pre><code>WHERE age > 18                           -- больше
WHERE age BETWEEN 18 AND 25               -- между
WHERE name LIKE 'Ива%'                    -- начинается с "Ива"
WHERE city IN ('Москва', 'СПб')           -- из списка
WHERE age IS NULL                         -- пустые значения
WHERE age > 18 AND city = 'Москва'        -- несколько условий</code></pre>
                            </div>
                        </details>

                        <details class="hint-details">
                            <summary class="hint-summary">📊 Агрегатные функции</summary>
                            <div class="hint-content">
                                <pre><code>SELECT COUNT(*) FROM student;             -- количество записей
SELECT AVG(age) FROM student;             -- среднее значение
SELECT SUM(scholarship) FROM enrollment;  -- сумма
SELECT MAX(age), MIN(age) FROM student;   -- максимум и минимум
SELECT faculty_id, COUNT(*) FROM student
GROUP BY faculty_id;                      -- группировка</code></pre>
                            </div>
                        </details>

                        <details class="hint-details">
                            <summary class="hint-summary">🔗 JOIN (объединение таблиц)</summary>
                            <div class="hint-content">
                                <pre><code>-- INNER JOIN (только совпадающие записи)
SELECT s.full_name, e.year_of_enrollment
FROM student s
JOIN enrollment e ON s.id = e.student_id;

-- LEFT JOIN (все студенты, даже без зачисления)
SELECT s.full_name, e.year_of_enrollment
FROM student s
LEFT JOIN enrollment e ON s.id = e.student_id;</code></pre>
                            </div>
                        </details>

                        <details class="hint-details">
                            <summary class="hint-summary">⌨️ Горячие клавиши</summary>
                            <div class="hint-content">
                                <pre><code>Ctrl + Enter      → Выполнить запрос
Ctrl + Space      → Автодополнение (таблицы, колонки)
Ctrl + Shift + F  → Форматировать SQL
Tab               → Отступ (4 пробела)</code></pre>
                            </div>
                        </details>

                        <details class="hint-details">
                            <summary class="hint-summary">⚠️ Частые ошибки и их решение</summary>
                            <div class="hint-content">
                                <ul>
                                    <li><strong>Ошибка синтаксиса</strong> → Проверьте запятые и кавычки</li>
                                    <li><strong>Колонка не существует</strong> → Проверьте название колонки</li>
                                    <li><strong>Таблица не существует</strong> → Выберите правильную базу данных</li>
                                    <li><strong>Таймаут запроса</strong> → Добавьте LIMIT или оптимизируйте запрос</li>
                                    <li><strong>Не хватает прав</strong> → Разрешены только SELECT запросы</li>
                                </ul>
                            </div>
                        </details>
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
        </div>
    </main>

    <!-- CodeMirror JS -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/mode/sql/sql.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/show-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/sql-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/matchbrackets.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/closebrackets.min.js"></script>

    <script src="script.js"></script>
</body>
</html>