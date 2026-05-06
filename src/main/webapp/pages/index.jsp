<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Trainer - учебная платформа</title>
    <link rel="stylesheet" href="/css/style.css?v=2">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/theme/dracula.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/show-hint.min.css">
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Учебная песочница</span>
            </div>
            <div class="nav-right" id="navRight">
                <a href="index" class="nav-link active">Тренажёр</a>
                <a href="teacher" id="teacherLinkBtn" class="nav-link" style="display: none;">Панель преподавателя</a>
                <a href="#" id="logoutBtn" class="nav-link" style="display: none;">Выйти</a>
            </div>
        </div>
    </nav>

    <main class="container">
        <div class="page-header">
            <h2 class="page-title">Выполнение SQL-запросов</h2>
            <p class="page-description">Выберите базу данных и напишите запрос</p>
        </div>

        <div class="two-columns">
            <div class="left-column">
                <div class="card">
                    <h3 class="card-title">📁 Выберите базу данных</h3>

                    <div class="folder-selector" style="margin-bottom: 1rem;">
                        <select id="folderSelector" class="db-select" onchange="changeFolder(this.value)" required>
                            <option value="" disabled selected hidden>Выберите папку...</option>
                        </select>
                    </div>

                    <div class="db-selector">
                        <select id="dbSelector" class="db-select" onchange="changeDatabase(this.value)" disabled required>
                            <option value="" disabled selected hidden>Сначала выберите папку</option>
                        </select>
                    </div>

                    <div id="passwordModal" style="display: none; margin-top: 1rem; padding: 1rem; background: var(--light); border-radius: var(--radius);">
                        <label for="dbPassword" style="display: block; margin-bottom: 0.5rem;">Введите пароль доступа к базе:</label>
                        <input type="password" id="dbPassword" class="form-input" style="width: 100%; margin-bottom: 0.5rem;">
                        <button id="submitPasswordBtn" class="btn btn-primary" style="width: 100%;">Подтвердить</button>
                        <div id="passwordError" style="color: red; margin-top: 0.5rem; display: none;"></div>
                    </div>

                    <div class="db-info-card" id="dbInfoCard" style="display: none;">
                        <div class="db-header">
                            <span class="db-icon">🗄️</span>
                            <span class="db-name" id="dbName"></span>
                        </div>

                        <!-- Сворачиваемая схема базы данных -->
                        <div id="schemaContainer" class="schema-collapsible" style="display: none;">
                            <div class="schema-header" id="schemaHeader">
                                <div class="schema-title">
                                    <span>📊</span>
                                    <span>Схема базы данных</span>
                                </div>
                                <div class="schema-toggle" id="schemaToggle">▲</div>
                            </div>
                            <div class="schema-content" id="schemaContent">
                                <img id="schemaImage" class="schema-image" src="" alt="Схема базы данных">
                                <div style="font-size: 0.75rem; color: var(--text-light); margin-top: 0.5rem; text-align: center;">
                                    ⚡ Нажмите на изображение для увеличения
                                </div>
                            </div>
                        </div>

                        <div class="db-tables-section">
                            <div class="tables-header">
                                <h4>Таблицы в базе</h4>
                                <span class="tables-count" id="tablesCount">0</span>
                            </div>
                            <div class="tables-grid" id="tablesList">
                                <div class="empty-state">Загрузка...</div>
                            </div>
                        </div>

                        <div class="connection-status">
                            <div class="status-left">
                                <span class="status-indicator online"></span>
                                <span class="status-text">PostgreSQL</span>
                            </div>
                            <span class="db-badge" id="currentDbBadge" title=""></span>
                        </div>
                    </div>
                </div>

                <div class="card hints-card">
                    <h3 class="card-title">⌨️ Горячие клавиши</h3>
                    <div class="hints-container">
                        <details class="hint-details">
                            <summary class="hint-summary">▶ Показать горячие клавиши</summary>
                            <div class="hint-content">
                                <div class="hotkeys-grid">
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Ctrl + Enter</span>
                                        <span class="hotkey-desc">Выполнить запрос</span>
                                    </div>
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Ctrl + Space</span>
                                        <span class="hotkey-desc">Автодополнение</span>
                                    </div>
                                    <div class="hotkey-item">
                                        <span class="hotkey-key">Ctrl + Shift + F</span>
                                        <span class="hotkey-desc">Форматировать SQL</span>
                                    </div>
                                </div>
                            </div>
                        </details>
                    </div>
                </div>
            </div>

            <div class="right-column">
                <div class="card">
                    <h3 class="card-title">✏️ SQL запрос</h3>

                    <div class="editor-container">
                        <textarea id="sqlQuery" class="sql-editor">SELECT * FROM student LIMIT 10;</textarea>

                        <div class="editor-toolbar">
                            <button id="executeBtn" class="btn btn-primary">▶ Выполнить (Ctrl+Enter)</button>
                            <button id="clearBtn" class="btn btn-secondary">🗑️ Очистить</button>
                            <button id="formatBtn" class="btn btn-secondary" title="Форматировать SQL (Ctrl+Shift+F)">✨ Формат</button>
                            <button id="downloadBtn" class="btn btn-secondary">📥 Скачать CSV</button>

                            <label class="explain-checkbox" title="Показывать план выполнения запроса">
                                <input type="checkbox" id="showExplainCheckbox">
                                <span class="checkbox-icon">📊</span>
                                <span class="checkbox-text">EXPLAIN</span>
                            </label>

                            <span id="executionTime" class="execution-time"></span>
                        </div>
                    </div>

                    <div class="results-section">
                        <div class="results-header">
                            <h4 class="results-title">Результат:</h4>
                            <span id="rowCount" class="row-count"></span>
                        </div>
                        <div id="resultContainer" class="results-table">
                            <div class="empty-state">Выберите базу данных и выполните запрос</div>
                        </div>
                    </div>

                    <div class="explain-section">
                        <details class="explain-details">
                            <summary class="explain-summary">📊 Показать план выполнения (EXPLAIN ANALYZE)</summary>
                            <div class="explain-toolbar">
                                <button class="explain-view-btn active" data-view="tree">🌳 Дерево</button>
                                <button class="explain-view-btn" data-view="text">📄 Текст</button>
                            </div>
                            <div id="explainTreeView" class="explain-tree-container"></div>
                            <pre id="explainTextView" class="explain-output" style="display: none;">-- Здесь появится вывод EXPLAIN ANALYZE</pre>
                        </details>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <!-- Модальное окно для увеличения схемы -->
    <div id="imageModal" class="image-modal" onclick="closeImageModal()">
        <span class="image-modal-close" onclick="closeImageModal()">&times;</span>
        <div class="image-modal-content">
            <img id="modalImage" src="" alt="Увеличенная схема">
        </div>
    </div>

    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/mode/sql/sql.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/show-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/hint/sql-hint.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/matchbrackets.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/addon/edit/closebrackets.min.js"></script>
    <script src="/js/script.js?v=2"></script>
</body>
</html>