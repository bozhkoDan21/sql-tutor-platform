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
    <meta name="csrf-token" content="">
    <title>SQL Trainer - Панель преподавателя</title>
    <link rel="stylesheet" href="/css/style.css?v=2">
    <style>
        /* Стили для модального окна загрузки с прогресс-баром */
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

        /* Вкладки */
        .tabs {
            display: flex;
            gap: 0.5rem;
            margin-bottom: 1.5rem;
            border-bottom: 2px solid var(--border);
        }
        .tab {
            padding: 0.75rem 1.5rem;
            background: none;
            border: none;
            font-size: 1rem;
            font-weight: 600;
            color: var(--text-light);
            cursor: pointer;
            transition: all 0.2s;
        }
        .tab:hover {
            color: var(--primary);
        }
        .tab.active {
            color: var(--primary);
            border-bottom: 2px solid var(--primary);
            margin-bottom: -2px;
        }
        .tab-content {
            display: none;
        }
        .tab-content.active {
            display: block;
        }

        /* Форма создания папки */
        .folder-create {
            display: flex;
            gap: 0.5rem;
            margin-bottom: 1rem;
        }
        .folder-create input {
            flex: 1;
        }

        /* Список папок */
        .folders-list {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }
        .folder-item {
            background: var(--light);
            border-radius: var(--radius);
            border: 1px solid var(--border);
            overflow: hidden;
        }
        .folder-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.75rem 1rem;
            background: var(--white);
            cursor: pointer;
            transition: background 0.2s;
        }
        .folder-header:hover {
            background: var(--primary-light);
        }
        .folder-name {
            font-weight: 600;
            color: var(--dark);
        }
        .folder-databases {
            padding: 0.5rem 1rem;
            display: none;
            flex-direction: column;
            gap: 0.5rem;
            border-top: 1px solid var(--border);
        }
        .folder-databases.open {
            display: flex;
        }
        .folder-db-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.5rem;
            background: var(--white);
            border-radius: var(--radius-sm);
            border: 1px solid var(--border);
        }
        .folder-db-name {
            font-family: monospace;
            font-weight: bold;
        }
        .folder-db-display {
            color: var(--text-light);
            font-size: 0.85rem;
        }
        .btn-edit {
            background: none;
            border: 2px solid var(--primary-light);
            color: var(--primary);
            cursor: pointer;
            font-size: 0.75rem;
            padding: 0.25rem 0.75rem;
            border-radius: 2rem;
            transition: all 0.2s;
            font-weight: 500;
            margin-right: 0.5rem;
        }
        .btn-edit:hover {
            background: var(--primary);
            border-color: var(--primary);
            color: white;
        }
        .btn-delete {
            background: none;
            border: 2px solid var(--danger-light);
            color: var(--danger);
            cursor: pointer;
            font-size: 0.75rem;
            padding: 0.25rem 0.75rem;
            border-radius: 2rem;
            transition: all 0.2s;
            font-weight: 500;
        }
        .btn-delete:hover {
            background: var(--danger);
            border-color: var(--danger);
            color: white;
        }

        /* Список баз для управления */
        .db-manager-list {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }
        .db-manager-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1rem 1.25rem;
            background: linear-gradient(135deg, var(--light) 0%, #f3f4f6 100%);
            border-radius: var(--radius);
            border: 1px solid var(--border);
            transition: all 0.2s;
        }
        .db-manager-item:hover {
            transform: translateX(4px);
            border-color: var(--primary);
            box-shadow: var(--shadow);
        }
        .db-manager-item .db-info {
            display: flex;
            align-items: center;
            gap: 1rem;
            flex-wrap: wrap;
        }
        .db-manager-item .db-name {
            font-family: monospace;
            font-weight: bold;
            color: var(--primary);
        }
        .db-manager-item .db-meta {
            color: var(--text-light);
            font-size: 0.875rem;
            background: var(--white);
            padding: 0.25rem 0.75rem;
            border-radius: 2rem;
        }
        .db-actions {
            display: flex;
            gap: 0.5rem;
        }

        /* Модальное окно редактирования */
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            justify-content: center;
            align-items: center;
            z-index: 1000;
        }
        .modal-content {
            background: white;
            padding: 2rem;
            border-radius: 1rem;
            max-width: 500px;
            width: 90%;
            max-height: 90%;
            overflow-y: auto;
        }
        .modal-content h3 {
            margin-bottom: 1rem;
            color: var(--dark);
        }
        .form-buttons {
            display: flex;
            gap: 1rem;
            justify-content: flex-end;
            margin-top: 1.5rem;
        }

        /* Загрузка схемы */
        .schema-preview {
            margin-top: 1rem;
            padding: 1rem;
            background: var(--light);
            border-radius: var(--radius);
            display: none;
        }
        .schema-preview img {
            max-width: 100%;
            border-radius: var(--radius);
            margin-top: 0.5rem;
        }

        /* Общие стили форм */
        .form-group {
            margin-bottom: 1rem;
        }
        .form-label {
            display: block;
            margin-bottom: 0.5rem;
            font-weight: 600;
            color: var(--dark);
        }
        .form-input {
            width: 100%;
            padding: 0.75rem;
            border: 2px solid var(--border);
            border-radius: var(--radius);
            font-size: 0.95rem;
            transition: all 0.2s;
            box-sizing: border-box;
        }
        .form-input:focus {
            outline: none;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
        }
        .file-upload {
            border: 2px dashed var(--border);
            border-radius: var(--radius);
            padding: 1.5rem;
            text-align: center;
            transition: all 0.2s;
            background: var(--light);
            cursor: pointer;
        }
        .file-upload:hover {
            border-color: var(--primary);
            background: var(--primary-light);
        }
        .file-input {
            display: none;
        }
        .file-label {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 0.75rem;
            color: var(--text-light);
            cursor: pointer;
        }
        .file-icon {
            font-size: 2rem;
        }
        .form-actions {
            display: flex;
            justify-content: flex-end;
            margin-top: 1rem;
        }
        .btn {
            padding: 0.625rem 1.25rem;
            border: none;
            border-radius: 2rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 0.95rem;
        }
        .btn-primary {
            background: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
            color: var(--white);
            box-shadow: 0 2px 4px rgba(79, 70, 229, 0.3);
        }
        .btn-primary:hover {
            background: linear-gradient(135deg, var(--primary-hover) 0%, #4f46e5 100%);
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(79, 70, 229, 0.4);
        }
        .btn-secondary {
            background: var(--white);
            color: var(--text);
            border: 2px solid var(--border);
        }
        .btn-secondary:hover {
            background: var(--light);
            border-color: var(--text-light);
            transform: translateY(-2px);
        }
        .empty-state {
            padding: 2rem;
            text-align: center;
            color: var(--text-light);
            font-style: italic;
        }
        .card {
            background: var(--white);
            border-radius: var(--radius);
            box-shadow: var(--shadow);
            padding: 1.5rem;
            margin-bottom: 1.5rem;
        }
        .card-title {
            font-size: 1.125rem;
            font-weight: 600;
            margin-bottom: 1.25rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
            color: var(--dark);
            padding-bottom: 0.75rem;
            border-bottom: 2px solid var(--primary-light);
        }
        .page-title {
            font-size: 2rem;
            font-weight: bold;
            color: var(--dark);
            margin-bottom: 1.5rem;
        }
        .navbar {
            background: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
            color: var(--white);
            box-shadow: var(--shadow-lg);
        }
        .nav-container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.5rem;
            height: 4rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .nav-left {
            display: flex;
            align-items: center;
            gap: 1rem;
        }
        .logo {
            font-size: 1.5rem;
            font-weight: bold;
            background: linear-gradient(135deg, #fff 0%, #e0e7ff 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        .badge {
            background: rgba(255, 255, 255, 0.2);
            padding: 0.25rem 0.75rem;
            border-radius: 2rem;
            font-size: 0.75rem;
            font-weight: 500;
        }
        .nav-right {
            display: flex;
            gap: 0.5rem;
        }
        .nav-link {
            color: rgba(255, 255, 255, 0.8);
            text-decoration: none;
            padding: 0.5rem 1rem;
            border-radius: 2rem;
            transition: all 0.2s;
            font-weight: 500;
        }
        .nav-link:hover {
            background: rgba(255, 255, 255, 0.15);
            color: white;
        }
        .nav-link.active {
            background: white;
            color: var(--primary);
        }
        .container {
            max-width: 1280px;
            margin: 2rem auto;
            padding: 0 1.5rem;
        }
    </style>
</head>
<body>
    <!-- Оверлей загрузки с прогресс-баром и логами -->
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

    <!-- Модальное окно редактирования базы данных -->
    <div id="editDatabaseModal" class="modal">
        <div class="modal-content">
            <h3>✏️ Редактировать базу данных</h3>
            <input type="hidden" id="editDbName">
            <input type="hidden" name="csrf_token" id="editCsrfToken" value="">
            <div class="form-group">
                <label>Отображаемое имя</label>
                <input type="text" id="editDisplayName" class="form-input">
            </div>
            <div class="form-group">
                <label>Папка</label>
                <select id="editFolderId" class="form-input"></select>
            </div>
            <div class="form-group">
                <label>Пароль доступа (оставьте пустым для сохранения текущего)</label>
                <input type="password" id="editAccessPassword" class="form-input" placeholder="Новый пароль">
                <label style="display: flex; align-items: center; gap: 0.5rem; margin-top: 0.5rem; cursor: pointer;">
                    <input type="checkbox" id="editRemovePasswordCheckbox" style="width: auto;">
                    <span style="font-size: 0.85rem; color: var(--danger);">❌ Удалить пароль (сделать базу открытой)</span>
                </label>
            </div>
            <div class="form-group">
                <label>Видимость для студентов</label>
                <select id="editIsVisible" class="form-input">
                    <option value="true">Видима</option>
                    <option value="false">Скрыта</option>
                </select>
            </div>
            <div class="form-group">
                <label>Начало доступа (оставьте пустым для безлимита)</label>
                <input type="date" id="editAccessStart" class="form-input">
            </div>
            <div class="form-group">
                <label>Конец доступа (оставьте пустым для безлимита)</label>
                <input type="date" id="editAccessEnd" class="form-input">
            </div>
            <div class="form-buttons">
                <button class="btn btn-secondary" onclick="closeEditModal()">Отмена</button>
                <button class="btn btn-primary" onclick="saveDatabaseMetadata()">Сохранить</button>
            </div>
        </div>
    </div>

    <!-- Навигационная панель -->
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Панель преподавателя</span>
            </div>
            <div class="nav-right">
                <a href="index" class="nav-link">Тренажёр</a>
                <a href="teacher" class="nav-link active">Панель преподавателя</a>
                <a href="#" id="logoutBtn" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <!-- Основной контент -->
    <main class="container">
        <h2 class="page-title">Панель управления</h2>
        <div class="tabs">
            <button class="tab active" data-tab="databases">🗄️ Базы данных</button>
            <button class="tab" data-tab="folders">📁 Папки</button>
        </div>
        <!-- Вкладка: Базы данных -->
        <div id="tab-databases" class="tab-content active">
            <!-- Карточка загрузки новой базы данных -->
            <div class="card">
                <h3 class="card-title">📤 Загрузить новую учебную базу</h3>
                <form id="uploadForm" class="upload-form" autocomplete="off">
                    <input type="hidden" name="csrf_token" id="csrfTokenField1" value="">
                    <div class="form-group">
                        <label class="form-label">Название базы данных (латиница, без пробелов)</label>
                        <input type="text" id="dbName" class="form-input" placeholder="например: my_database" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Папка</label>
                        <select id="folderSelect" class="form-input" required></select>
                    </div>
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
                    <div class="form-group">
                        <label class="form-label">Отображаемое имя (для студентов)</label>
                        <input type="text" id="displayName" class="form-input" placeholder="Имя для отображения студентам">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Пароль доступа (оставьте пустым для открытого доступа)</label>
                        <input type="password" id="accessPassword" class="form-input">
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Создать базу данных</button>
                    </div>
                </form>
            </div>

            <!-- Карточка загрузки схемы базы данных -->
            <div class="card">
                <h3 class="card-title">🖼️ Загрузить схему базы данных</h3>
                <form id="uploadSchemaForm" class="upload-form" autocomplete="off">
                    <input type="hidden" name="csrf_token" id="csrfTokenField2" value="">
                    <div class="form-group">
                        <label class="form-label">Выберите базу данных</label>
                        <select id="schemaDbSelect" class="form-input" required>
                            <option value="">Выберите базу</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Изображение схемы (JPEG, PNG, GIF)</label>
                        <div class="file-upload">
                            <input type="file" id="schemaImage" accept="image/jpeg,image/png,image/gif" class="file-input" onchange="updateSchemaFileLabel(this)" required>
                            <label for="schemaImage" class="file-label" id="schemaFileLabel">
                                <span class="file-icon">🖼️</span>
                                <span id="schemaFileName">Выберите файл схемы</span>
                            </label>
                        </div>
                        <div style="font-size: 0.8rem; color: var(--text-light); margin-top: 0.25rem;">Максимальный размер: 5 MB</div>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Загрузить схему</button>
                    </div>
                </form>
                <div id="schemaPreview" class="schema-preview">
                    <strong>Текущая схема:</strong>
                    <img id="schemaPreviewImg" src="" alt="Схема базы данных">
                </div>
            </div>

            <!-- Карточка генерации вопросов для Moodle -->
            <div class="card">
                <h3 class="card-title">📚 Генерация вопросов для Moodle</h3>
                <form id="moodleForm" class="upload-form" autocomplete="off">
                    <input type="hidden" name="csrf_token" id="csrfTokenField3" value="">
                    <div class="form-group">
                        <label class="form-label">Выберите базу данных</label>
                        <select id="moodleDbSelect" class="form-input" required>
                            <option value="">Выберите базу</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Название категории (опционально)</label>
                        <input type="text" id="moodleCategory" class="form-input" placeholder="SQL Questions from ...">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Формат вывода</label>
                        <select id="moodleFormat" class="form-input">
                            <option value="gift">GIFT (рекомендуется, работает в любом Moodle)</option>
                            <option value="xml">Moodle XML (требуется плагин CodeRunner)</option>
                            <option value="text">Текстовый просмотр (для проверки)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Файл с вопросами</label>
                        <div class="file-upload">
                            <input type="file" id="questionsFile" accept=".txt" class="file-input" onchange="updateMoodleFileLabel(this)" required>
                            <label for="questionsFile" class="file-label">
                                <span class="file-icon">📄</span>
                                <span id="moodleFileName">Выберите файл (.txt)</span>
                            </label>
                        </div>
                        <div style="font-size: 0.8rem; color: var(--text-light); margin-top: 0.25rem;">
                            Формат: каждая пара строк - вопрос, затем SQL запрос
                        </div>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Генерировать</button>
                    </div>
                </form>
                <div id="moodleResult" style="margin-top: 1rem; display: none;"></div>
            </div>

            <!-- Карточка списка существующих баз данных -->
            <div class="card">
                <h3 class="card-title">🗄️ Существующие базы данных</h3>
                <div class="db-manager-list" id="databasesList"><div class="empty-state">Загрузка...</div></div>
            </div>
        </div>

        <!-- Вкладка: Папки -->
        <div id="tab-folders" class="tab-content">
            <div class="card">
                <h3 class="card-title">📁 Управление папками</h3>
                <div class="folder-create">
                    <input type="text" id="newFolderName" class="form-input" placeholder="Название новой папки">
                    <button id="createFolderBtn" class="btn btn-primary">Создать папку</button>
                </div>
                <div class="folders-list" id="foldersList"><div class="empty-state">Загрузка...</div></div>
            </div>
        </div>
    </main>

    <!-- Подключение JavaScript -->
    <script src="/js/teacher.js?v=3"></script>
</body>
</html>