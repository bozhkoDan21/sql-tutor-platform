let sqlEditor = null;
let currentDbRequest = null;
let currentDbName = null;
let currentTables = [];
let currentTablesWithColumns = {};
let foldersData = [];
let currentPasswordCallback = null;
let pendingDbName = null;
let pendingSchemaImageUrl = null;

let sqlKeywords = [
    'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'OUTER JOIN',
    'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'AND', 'OR', 'NOT',
    'IN', 'EXISTS', 'BETWEEN', 'LIKE', 'IS NULL', 'IS NOT NULL', 'AS', 'ON',
    'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'DISTINCT', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END'
];

/**
 * Экранирует HTML-символы для предотвращения XSS-атак
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Загружает список папок и баз данных
 */
function loadDatabasesList() {
    fetch('/api/databases')
        .then(response => response.json())
        .then(data => {
            if (data.success && data.folders) {
                foldersData = data.folders;
                renderFolderSelector();
            } else {
                console.error('Failed to load databases:', data.error);
            }
        })
        .catch(error => {
            console.error('Error loading databases:', error);
            const folderSelect = document.getElementById('folderSelector');
            if (folderSelect) {
                folderSelect.innerHTML = '<option value="">Ошибка загрузки</option>';
            }
        });
}

/**
 * Отображает селектор папок
 */
function renderFolderSelector() {
    const folderSelect = document.getElementById('folderSelector');
    if (!folderSelect) return;

    // Очищаем все опции, оставляем только плейсхолдер
    folderSelect.innerHTML = '<option value="" disabled selected hidden>Выберите папку...</option>';

    for (const folder of foldersData) {
        const option = document.createElement('option');
        option.value = folder.id;
        option.textContent = folder.name + ' (' + folder.databases.length + ' баз)';
        folderSelect.appendChild(option);
    }

    // Сбрасываем валидацию
    folderSelect.value = "";
}

/**
 * Смена выбранной папки
 */
function changeFolder(folderId) {
    const dbSelect = document.getElementById('dbSelector');

    // Если не выбрана папка
    if (!folderId || folderId === "") {
        // Очищаем селектор баз
        dbSelect.innerHTML = '<option value="" disabled selected hidden>Сначала выберите папку</option>';
        dbSelect.disabled = true;
        dbSelect.value = "";

        // Очищаем всю информацию
        clearAllDatabaseInfo();
        return;
    }

    const folder = foldersData.find(f => f.id == folderId);

    if (!folder || folder.databases.length === 0) {
        dbSelect.innerHTML = '<option value="" disabled selected hidden>Нет доступных баз</option>';
        dbSelect.disabled = true;
        dbSelect.value = "";

        // Очищаем всю информацию
        clearAllDatabaseInfo();
        return;
    }

    // Заполняем список баз
    dbSelect.disabled = false;
    dbSelect.innerHTML = '<option value="" disabled selected hidden>Выберите базу данных...</option>';

    for (const db of folder.databases) {
        const option = document.createElement('option');
        option.value = db.dbName;
        option.textContent = db.displayName + (db.hasPassword ? ' 🔒' : '');
        option.dataset.hasPassword = db.hasPassword;
        option.dataset.schemaImageUrl = db.schemaImageUrl || '';
        dbSelect.appendChild(option);
    }

    // Сбрасываем значение
    dbSelect.value = "";

    // Очищаем всю информацию при смене папки
    clearAllDatabaseInfo();
}

/**
 * Полная очистка информации о базе данных
 */
function clearAllDatabaseInfo() {
    // Скрываем карточку БД
    const dbInfoCard = document.getElementById('dbInfoCard');
    if (dbInfoCard) dbInfoCard.style.display = 'none';

    // Скрываем и очищаем схему
    hideSchemaImage();

    // Очищаем бейдж
    const currentDbBadge = document.getElementById('currentDbBadge');
    if (currentDbBadge) currentDbBadge.textContent = '';

    // Очищаем список таблиц
    const tablesList = document.getElementById('tablesList');
    const tablesCountSpan = document.getElementById('tablesCount');
    if (tablesList) tablesList.innerHTML = '<div class="empty-state">Выберите базу данных</div>';
    if (tablesCountSpan) tablesCountSpan.textContent = '0';

    // Очищаем результаты запроса
    const resultContainer = document.getElementById('resultContainer');
    const executionTimeSpan = document.getElementById('executionTime');
    const rowCountSpan = document.getElementById('rowCount');
    if (resultContainer) resultContainer.innerHTML = '<div class="empty-state">Выберите базу данных и выполните запрос</div>';
    if (executionTimeSpan) executionTimeSpan.textContent = '';
    if (rowCountSpan) rowCountSpan.textContent = '';

    // Очищаем EXPLAIN
    const treeView = document.getElementById('explainTreeView');
    const textView = document.getElementById('explainTextView');
    if (treeView) treeView.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
    if (textView) textView.textContent = '-- Здесь появится вывод EXPLAIN ANALYZE';

    // Сбрасываем глобальные переменные
    currentDbName = null;
    currentTables = [];
    currentTablesWithColumns = {};

    // Очищаем авторизацию пароля (если была)
    const passwordModal = document.getElementById('passwordModal');
    if (passwordModal) passwordModal.style.display = 'none';
}

/**
 * Смена базы данных
 */
function changeDatabase(dbName) {
    const dbSelect = document.getElementById('dbSelector');

    // Если выбрана пустая опция (плейсхолдер)
    if (!dbName || dbName === "") {
        clearAllDatabaseInfo();
        dbSelect.value = "";
        return;
    }

    const selectedOption = dbSelect.options[dbSelect.selectedIndex];
    const hasPassword = selectedOption && selectedOption.dataset.hasPassword === 'true';
    const schemaImageUrl = selectedOption ? selectedOption.dataset.schemaImageUrl : '';

    if (hasPassword) {
        // Сохраняем информацию для после ввода пароля
        pendingDbName = dbName;
        pendingSchemaImageUrl = schemaImageUrl;

        // Показываем модальное окно
        const passwordModal = document.getElementById('passwordModal');
        const passwordInput = document.getElementById('dbPassword');
        const passwordError = document.getElementById('passwordError');

        if (passwordError) passwordError.style.display = 'none';
        if (passwordInput) passwordInput.value = '';
        if (passwordModal) passwordModal.style.display = 'block';

        currentPasswordCallback = () => {
            const enteredPassword = passwordInput ? passwordInput.value.trim() : '';
            if (!enteredPassword) {
                if (passwordError) {
                    passwordError.textContent = 'Введите пароль';
                    passwordError.style.display = 'block';
                }
                return false;
            }
            verifyDatabasePassword(dbName, enteredPassword);
            return true;
        };
        return;
    }

    // Без пароля - сразу загружаем
    loadDatabaseAfterAuth(dbName, schemaImageUrl);
}

/**
 * Загрузка базы данных после успешной аутентификации
 */
function loadDatabaseAfterAuth(dbName, schemaImageUrl) {
    loadDbInfo(dbName);

    // Показываем схему
    if (schemaImageUrl && schemaImageUrl !== 'null' && schemaImageUrl !== '') {
        showSchemaImage(schemaImageUrl);
    } else {
        hideSchemaImage();
    }

    updateCurrentDbBadge(dbName);
}

/**
 * Проверка пароля базы данных
 */
function verifyDatabasePassword(dbName, password) {
    fetch('/api/database/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dbName: dbName, password: password })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            const passwordModal = document.getElementById('passwordModal');
            if (passwordModal) passwordModal.style.display = 'none';

            // Загружаем базу после успешного ввода пароля
            loadDatabaseAfterAuth(dbName, pendingSchemaImageUrl);

            // Очищаем pending переменные
            pendingDbName = null;
            pendingSchemaImageUrl = null;
        } else {
            const passwordError = document.getElementById('passwordError');
            if (passwordError) {
                passwordError.textContent = data.error || 'Неверный пароль';
                passwordError.style.display = 'block';
            }
        }
    })
    .catch(() => {
        const passwordError = document.getElementById('passwordError');
        if (passwordError) {
            passwordError.textContent = 'Ошибка проверки пароля';
            passwordError.style.display = 'block';
        }
    });
}

/**
 * Открыть модальное окно с увеличенным изображением
 */
function openImageModal(imageUrl) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('modalImage');

    if (modal && modalImg && imageUrl) {
        modal.style.display = 'block';
        modalImg.src = imageUrl;
    }
}

/**
 * Закрыть модальное окно
 */
function closeImageModal() {
    const modal = document.getElementById('imageModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

/**
 * Закрыть модальное окно по клавише Escape
 */
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeImageModal();
    }
});


/**
 * Обновляет бейдж с именем текущей базы
 */
function updateCurrentDbBadge(dbName) {
    const badge = document.getElementById('currentDbBadge');
    if (badge) {
        badge.textContent = dbName;
        badge.title = dbName;
    }
    const dbInfoCard = document.getElementById('dbInfoCard');
    if (dbInfoCard) {
        dbInfoCard.style.display = 'block';
    }
    const dbNameElement = document.getElementById('dbName');
    if (dbNameElement) {
        dbNameElement.textContent = dbName;
    }
}

/**
 * Загрузка информации о базе данных (таблицы)
 */
function loadDbInfo(dbName) {
    if (!dbName) return;

    currentDbName = dbName;

    if (currentDbRequest) {
        currentDbRequest.abort();
        currentDbRequest = null;
    }

    const tablesList = document.getElementById('tablesList');
    const tablesCountSpan = document.getElementById('tablesCount');
    const dbNameElement = document.getElementById('dbName');
    const dbInfoCard = document.getElementById('dbInfoCard');

    if (dbNameElement) dbNameElement.textContent = dbName;
    if (dbInfoCard) dbInfoCard.style.display = 'block';
    if (tablesCountSpan) tablesCountSpan.textContent = '0';
    if (tablesList) tablesList.innerHTML = '<div class="empty-state">Загрузка таблиц...</div>';

    currentDbRequest = new AbortController();

    fetch('/api/dbinfo?db=' + encodeURIComponent(dbName), {
        signal: currentDbRequest.signal
    })
        .then(response => response.json())
        .then(async (data) => {
            if (currentDbName !== dbName) return;

            if (data.success && tablesList) {
                currentTables = data.tables || [];
                tablesList.innerHTML = '';

                if (tablesCountSpan) {
                    tablesCountSpan.textContent = currentTables.length;
                }

                if (currentTables.length > 0) {
                    currentTables.sort().forEach(table => {
                        const li = document.createElement('li');
                        li.textContent = table;
                        li.onclick = () => insertTableName(table);
                        li.title = 'Нажмите, чтобы вставить имя таблицы';
                        tablesList.appendChild(li);
                    });

                    await loadAllTableColumns(dbName, currentTables);
                } else {
                    tablesList.innerHTML = '<div class="empty-state">Нет таблиц</div>';
                }
            } else if (tablesList) {
                tablesList.innerHTML = `<div class="empty-state">Ошибка: ${data.error || 'Неизвестная ошибка'}</div>`;
            }
            currentDbRequest = null;
        })
        .catch(error => {
            if (error.name === 'AbortError') return;
            console.error('Error loading db info:', error);
            if (currentDbName === dbName && tablesList) {
                tablesList.innerHTML = '<div class="empty-state">Ошибка соединения</div>';
            }
            currentDbRequest = null;
        });
}

/**
 * Загружает колонки для всех таблиц
 */
async function loadAllTableColumns(dbName, tables) {
    for (const table of tables) {
        try {
            const response = await fetch(`/api/columns?db=${encodeURIComponent(dbName)}&table=${encodeURIComponent(table)}`);
            const data = await response.json();
            if (data.success) {
                currentTablesWithColumns[table] = data.columns;
            }
        } catch (error) {
            console.error(`Failed to load columns for ${table}:`, error);
        }
    }
}

/**
 * Вставляет имя таблицы в редактор
 */
function insertTableName(tableName) {
    if (sqlEditor) {
        const cursor = sqlEditor.getCursor();
        const line = sqlEditor.getLine(cursor.line);
        const textBeforeCursor = line.substring(0, cursor.ch);

        let insertText = tableName;
        if (textBeforeCursor.length > 0 && !textBeforeCursor.endsWith(' ') && !textBeforeCursor.endsWith('.')) {
            insertText = ' ' + tableName;
        }

        sqlEditor.replaceRange(insertText, cursor);
        sqlEditor.focus();
    }
}

/**
 * Инициализация сворачиваемой схемы
 */
function initCollapsibleSchema() {
    const schemaContainer = document.getElementById('schemaContainer');
    if (!schemaContainer) return;

    const header = document.getElementById('schemaHeader');
    if (header) {
        header.addEventListener('click', function() {
            const content = document.getElementById('schemaContent');
            const toggle = document.getElementById('schemaToggle');
            if (content.classList.contains('collapsed')) {
                content.classList.remove('collapsed');
                toggle.textContent = '▲';
            } else {
                content.classList.add('collapsed');
                toggle.textContent = '▼';
            }
        });
    }
}

/**
 * Отображение схемы базы данных
 */
function showSchemaImage(imageUrl) {
    const schemaContainer = document.getElementById('schemaContainer');
    const schemaImg = document.getElementById('schemaImage');
    const schemaContent = document.getElementById('schemaContent');
    const schemaToggle = document.getElementById('schemaToggle');

    if (!schemaContainer || !schemaImg) return;

    if (imageUrl && imageUrl !== 'null' && imageUrl !== '') {
        let fullUrl = imageUrl;
        if (!imageUrl.startsWith('/')) {
            fullUrl = '/' + imageUrl;
        }

        schemaImg.src = fullUrl;
        schemaContainer.style.display = 'block';

        // Добавляем обработчик клика для увеличения
        schemaImg.onclick = function(e) {
            e.stopPropagation();
            openImageModal(fullUrl);
        };

        // Добавляем обработчик ошибки
        schemaImg.onerror = function() {
            console.error('Failed to load image:', fullUrl);
            schemaContainer.style.display = 'none';
        };

        if (schemaContent) {
            schemaContent.classList.remove('collapsed');
            if (schemaToggle) schemaToggle.textContent = '▲';
        }
    } else {
        schemaContainer.style.display = 'none';
    }
}

/**
 * Скрытие схемы
 */
function hideSchemaImage() {
    const schemaContainer = document.getElementById('schemaContainer');
    if (schemaContainer) {
        schemaContainer.style.display = 'none';
    }
}

/**
 * Форматирует SQL запрос
 */
function formatQuery(query) {
    if (!query) return '';

    let formatted = query;

    formatted = formatted.replace(/\bselect\b/gi, 'SELECT');
    formatted = formatted.replace(/\bfrom\b/gi, 'FROM');
    formatted = formatted.replace(/\bwhere\b/gi, 'WHERE');
    formatted = formatted.replace(/\bjoin\b/gi, 'JOIN');
    formatted = formatted.replace(/\bleft join\b/gi, 'LEFT JOIN');
    formatted = formatted.replace(/\bright join\b/gi, 'RIGHT JOIN');
    formatted = formatted.replace(/\binner join\b/gi, 'INNER JOIN');
    formatted = formatted.replace(/\bgroup by\b/gi, 'GROUP BY');
    formatted = formatted.replace(/\border by\b/gi, 'ORDER BY');
    formatted = formatted.replace(/\bhaving\b/gi, 'HAVING');
    formatted = formatted.replace(/\blimit\b/gi, 'LIMIT');
    formatted = formatted.replace(/\boffset\b/gi, 'OFFSET');
    formatted = formatted.replace(/\band\b/gi, 'AND');
    formatted = formatted.replace(/\bor\b/gi, 'OR');
    formatted = formatted.replace(/\bnot\b/gi, 'NOT');
    formatted = formatted.replace(/\bin\b/gi, 'IN');
    formatted = formatted.replace(/\bexists\b/gi, 'EXISTS');
    formatted = formatted.replace(/\bbetween\b/gi, 'BETWEEN');
    formatted = formatted.replace(/\blike\b/gi, 'LIKE');
    formatted = formatted.replace(/\bis null\b/gi, 'IS NULL');
    formatted = formatted.replace(/\bis not null\b/gi, 'IS NOT NULL');
    formatted = formatted.replace(/\bas\b/gi, 'AS');
    formatted = formatted.replace(/\bon\b/gi, 'ON');

    formatted = formatted.replace(/SELECT\*/gi, 'SELECT *');
    formatted = formatted.replace(/,([^\s])/g, ', $1');
    formatted = formatted.replace(/\s+/g, ' ');
    formatted = formatted.replace(/FROM/g, '\nFROM');
    formatted = formatted.replace(/WHERE/g, '\nWHERE');
    formatted = formatted.replace(/JOIN/g, '\nJOIN');
    formatted = formatted.replace(/LEFT JOIN/g, '\nLEFT JOIN');
    formatted = formatted.replace(/RIGHT JOIN/g, '\nRIGHT JOIN');
    formatted = formatted.replace(/GROUP BY/g, '\nGROUP BY');
    formatted = formatted.replace(/ORDER BY/g, '\nORDER BY');

    return formatted.trim();
}

/**
 * Выполняет SQL запрос
 */
function executeQuery() {
    const dbSelect = document.getElementById('dbSelector');
    const selectedDb = dbSelect ? dbSelect.value : null;

    if (!selectedDb) {
        alert('Выберите базу данных');
        return;
    }

    let query = '';
    if (sqlEditor) {
        const selection = sqlEditor.getSelection();
        if (selection && selection.trim().length > 0) {
            query = selection.trim();
        } else {
            query = sqlEditor.getValue().trim();
        }
    } else {
        const textarea = document.getElementById('sqlQuery');
        if (textarea) {
            query = textarea.value.trim();
        }
    }

    query = formatQuery(query);

    if (!query) {
        alert('Введите SQL запрос');
        return;
    }

    const resultContainer = document.getElementById('resultContainer');
    if (resultContainer) {
        resultContainer.innerHTML = '<div class="empty-state">⏳ Выполнение запроса...</div>';
    }

    const showExplainCheckbox = document.getElementById('showExplainCheckbox');
    const showExplain = showExplainCheckbox ? showExplainCheckbox.checked : true;

    const treeView = document.getElementById('explainTreeView');
    const textView = document.getElementById('explainTextView');
    if (treeView) treeView.innerHTML = showExplain ? '-- Получение плана выполнения...' : '-- EXPLAIN отключён.';
    if (textView) textView.textContent = showExplain ? '-- Получение плана выполнения...' : '-- EXPLAIN отключён.';

    const bodyParams = new URLSearchParams({
        'database': selectedDb,
        'query': query,
        'explain': showExplain ? 'true' : 'false'
    });

    fetch('/api/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: bodyParams
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            if (resultContainer) {
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${escapeHtml(data.error)}</div>`;
            }
        } else {
            displayResults(data);
        }
    })
    .catch(error => {
        if (resultContainer) {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        }
    });
}

/**
 * Отображает результаты запроса
 */
function displayResults(data) {
    const resultContainer = document.getElementById('resultContainer');
    const executionTimeSpan = document.getElementById('executionTime');
    const rowCountSpan = document.getElementById('rowCount');

    if (data.error) {
        if (resultContainer) {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${escapeHtml(data.error)}</div>`;
        }
        return;
    }

    if (executionTimeSpan) executionTimeSpan.textContent = `⏱️ Время: ${data.executionTimeMs} мс`;
    if (rowCountSpan) rowCountSpan.textContent = `${data.rows.length} строк`;

    const showExplainCheckbox = document.getElementById('showExplainCheckbox');
    const showExplain = showExplainCheckbox ? showExplainCheckbox.checked : true;

    if (!showExplain) {
        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');
        if (treeView) treeView.innerHTML = '-- EXPLAIN отключён.';
        if (textView) textView.textContent = '-- EXPLAIN отключён.';
    } else if (data.explainJson || data.explainText) {
        displayExplain(data.explainJson, data.explainText);
    }

    if (!data.rows || data.rows.length === 0) {
        if (resultContainer) {
            resultContainer.innerHTML = '<div class="empty-state">✅ Запрос выполнен, но не вернул данных</div>';
        }
        return;
    }

    let tableHTML = '<div class="table-wrapper"><table class="results-table">';
    tableHTML += '<thead><tr>';
    data.columns.forEach(col => {
        tableHTML += `<th>${escapeHtml(col)}</th>`;
    });
    tableHTML += '<tr></thead><tbody>';

    data.rows.forEach(row => {
        tableHTML += '<tr>';
        data.columns.forEach(col => {
            let value = row[col];
            if (value === null) value = 'NULL';
            else if (value === undefined) value = '—';
            else if (typeof value === 'object') value = JSON.stringify(value);
            else value = String(value);

            let displayValue = value.length > 100 ? value.substring(0, 100) + '...' : value;
            tableHTML += `<td title="${escapeHtml(value)}">${escapeHtml(displayValue)}</td>`;
        });
        tableHTML += '</tr>';
    });
    tableHTML += '</tbody></table></div>';

    if (resultContainer) {
        resultContainer.innerHTML = tableHTML;
    }
}

/**
 * Отображает план выполнения EXPLAIN
 */
function displayExplain(explainData, explainText) {
    const treeView = document.getElementById('explainTreeView');
    const textView = document.getElementById('explainTextView');

    if (!treeView || !textView) return;

    // Проверка на пустые данные
    const hasExplainData = explainData && explainData.trim && explainData.trim().length > 0;
    const hasExplainText = explainText && explainText.trim && explainText.trim().length > 0;

    if (!hasExplainData && !hasExplainText) {
        treeView.innerHTML = '<div class="empty-state">Нет данных EXPLAIN для этого запроса</div>';
        textView.textContent = '-- EXPLAIN ANALYZE не вернул данных';
        treeView.style.display = 'block';
        textView.style.display = 'none';
        return;
    }

    if (explainData && explainData !== explainText) {
        treeView.innerHTML = renderExplainTree(explainData);
    } else if (explainText) {
        treeView.innerHTML = renderExplainTree(explainText);
    } else {
        treeView.innerHTML = renderExplainTree(explainData);
    }

    if (explainText && explainText.trim().length > 0) {
        textView.textContent = explainText;
    } else if (typeof explainData === 'string' && explainData.trim().length > 0) {
        textView.textContent = explainData;
    } else {
        textView.textContent = '-- Нет данных EXPLAIN ANALYZE';
    }

    treeView.style.display = 'block';
    textView.style.display = 'none';

    const activeBtn = document.querySelector('.explain-view-btn[data-view="tree"]');
    if (activeBtn) {
        document.querySelectorAll('.explain-view-btn').forEach(b => b.classList.remove('active'));
        activeBtn.classList.add('active');
    }
}

/**
 * Очищает результаты
 */
function clearResult() {
    if (sqlEditor) {
        sqlEditor.setValue('SELECT * FROM student LIMIT 10;');
    }

    const resultContainer = document.getElementById('resultContainer');
    const executionTimeSpan = document.getElementById('executionTime');
    const rowCountSpan = document.getElementById('rowCount');

    if (resultContainer) {
        resultContainer.innerHTML = '<div class="empty-state">Выберите базу данных и выполните запрос</div>';
    }
    if (executionTimeSpan) executionTimeSpan.textContent = '';
    if (rowCountSpan) rowCountSpan.textContent = '';

    const showExplainCheckbox = document.getElementById('showExplainCheckbox');
    const showExplain = showExplainCheckbox ? showExplainCheckbox.checked : true;
    const treeView = document.getElementById('explainTreeView');
    const textView = document.getElementById('explainTextView');

    if (showExplain) {
        if (treeView) treeView.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
        if (textView) textView.textContent = '-- Здесь появится вывод EXPLAIN ANALYZE';
    } else {
        if (treeView) treeView.innerHTML = '-- EXPLAIN отключён.';
        if (textView) textView.textContent = '-- EXPLAIN отключён.';
    }
}

/**
 * Скачивает результаты в CSV
 */
function downloadCSV() {
    const dbSelect = document.getElementById('dbSelector');
    const selectedDb = dbSelect ? dbSelect.value : null;

    if (!selectedDb) {
        alert('Выберите базу данных');
        return;
    }

    let query = '';
    if (sqlEditor) {
        query = sqlEditor.getValue().trim();
    } else {
        const textarea = document.getElementById('sqlQuery');
        if (textarea) {
            query = textarea.value.trim();
        }
    }

    if (!query) {
        alert('Введите SQL запрос');
        return;
    }

    // Получаем выбранный разделитель
    const separatorSelect = document.getElementById('csvSeparator');
    let separator = separatorSelect ? separatorSelect.value : ',';
    if (separator === 'tab') separator = '\t';

    const resultContainer = document.getElementById('resultContainer');
    if (resultContainer) {
        resultContainer.innerHTML = '<div class="empty-state">⏳ Подготовка файла...</div>';
    }

    fetch('/api/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            'database': selectedDb,
            'query': query
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            if (resultContainer) {
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${data.error}</div>`;
            }
            return;
        }

        if (!data.signature) {
            console.error('Server signature missing');
            if (resultContainer) {
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: Не удалось получить подпись сервера</div>`;
            }
            return;
        }

        const signature = data.signature;

        let csv = '';
        csv += `# SQL Query: ${query.replace(/\n/g, ' ')}\n`;
        csv += `# Database: ${selectedDb}\n`;
        csv += `# Execution Time: ${data.executionTimeMs} ms\n`;
        csv += `# Rows: ${data.rows.length}\n`;
        csv += `# Separator: ${separatorSelect ? separatorSelect.value : ','}\n`;
        csv += `# Generated: ${new Date().toLocaleString()}\n`;
        csv += `# Signature: ${signature}\n`;
        csv += '\n';

        // Используем выбранный разделитель
        csv += data.columns.join(separator) + '\n';

        data.rows.forEach(row => {
            const rowData = data.columns.map(col => {
                let value = row[col];
                if (value === null) return 'NULL';
                if (typeof value === 'string') {
                    // Если значение содержит разделитель или кавычки, оборачиваем в двойные кавычки
                    if (value.includes(separator) || value.includes('"') || value.includes('\n')) {
                        return `"${value.replace(/"/g, '""')}"`;
                    }
                }
                return value;
            });
            csv += rowData.join(separator) + '\n';
        });

        const blob = new Blob(["\uFEFF" + csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);

        link.setAttribute('href', url);
        link.setAttribute('download', `query_${selectedDb}_${timestamp}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        displayResults(data);
    })
    .catch(error => {
        if (resultContainer) {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        }
    });
}

/**
 * Кастомный хинт для автодополнения CodeMirror
 */
function customSqlHint(cm) {
    const cursor = cm.getCursor();
    const token = cm.getTokenAt(cursor);
    const line = cm.getLine(cursor.line);
    const textBeforeCursor = line.substring(0, cursor.ch);

    let hints = [];
    let from = { line: cursor.line, ch: token.start };
    let to = { line: cursor.line, ch: token.end };

    const afterFromMatch = textBeforeCursor.match(/FROM\s+$/i);
    const afterJoinMatch = textBeforeCursor.match(/JOIN\s+$/i);
    const afterSelectMatch = textBeforeCursor.match(/SELECT\s+$/i);
    const afterWhereMatch = textBeforeCursor.match(/WHERE\s+$/i);
    const afterTableDot = textBeforeCursor.match(/(\w+)\.$/i);

    if (afterFromMatch || afterJoinMatch) {
        hints = currentTables;
    } else if (afterSelectMatch) {
        const allColumns = [];
        for (const table in currentTablesWithColumns) {
            if (currentTablesWithColumns[table]) {
                currentTablesWithColumns[table].forEach(col => {
                    allColumns.push(`${table}.${col}`);
                    allColumns.push(col);
                });
            }
        }
        hints = [...new Set([...allColumns, ...sqlKeywords])];
    } else if (afterWhereMatch) {
        const allColumns = [];
        for (const table in currentTablesWithColumns) {
            if (currentTablesWithColumns[table]) {
                currentTablesWithColumns[table].forEach(col => {
                    allColumns.push(`${table}.${col}`);
                    allColumns.push(col);
                });
            }
        }
        hints = [...new Set([...allColumns, ...sqlKeywords])];
    } else if (afterTableDot) {
        const tableName = afterTableDot[1];
        if (currentTablesWithColumns[tableName]) {
            hints = currentTablesWithColumns[tableName];
        } else {
            hints = sqlKeywords;
        }
    } else {
        hints = [...sqlKeywords, ...currentTables];
    }

    const currentWord = token.string;
    if (currentWord) {
        hints = hints.filter(h => h.toLowerCase().startsWith(currentWord.toLowerCase()));
    }

    return {
        list: hints,
        from: from,
        to: to
    };
}

/**
 * Форматирует и устанавливает запрос в редактор
 */
function formatQueryAndSet() {
    if (sqlEditor) {
        const currentQuery = sqlEditor.getValue();
        const formatted = formatQuery(currentQuery);
        if (formatted !== currentQuery) {
            sqlEditor.setValue(formatted);
            sqlEditor.focus();
        }
    }
}

/**
 * Инициализация страницы
 */
function initPage() {
    // Загрузка списка баз данных
    loadDatabasesList();

    // Инициализация CodeMirror
    initSqlEditor();

    // Инициализация кнопок
    initButtons();

    // Инициализация сворачиваемой схемы
    initCollapsibleSchema();

    // Инициализация переключения EXPLAIN
    initExplainToggle();

    // Инициализация навигации (кнопки панели преподавателя и выхода)
    initNavigation();
}

/**
 * Инициализация SQL редактора CodeMirror
 */
function initSqlEditor() {
    const textarea = document.getElementById('sqlQuery');
    if (!textarea) return;

    sqlEditor = CodeMirror.fromTextArea(textarea, {
        mode: 'text/x-sql',
        theme: 'dracula',
        lineNumbers: true,
        lineWrapping: true,
        matchBrackets: true,
        autoCloseBrackets: true,
        indentUnit: 4,
        tabSize: 4,
        extraKeys: {
            'Ctrl-Space': 'autocomplete',
            'Ctrl-Enter': () => executeQuery(),
            'Ctrl-Shift-F': () => formatQueryAndSet()
        }
    });

    sqlEditor.setOption('hintOptions', {
        hint: customSqlHint,
        completeSingle: false,
        alignWithWord: false
    });
}

/**
 * Инициализация кнопок управления
 */
function initButtons() {
    const executeBtn = document.getElementById('executeBtn');
    const clearBtn = document.getElementById('clearBtn');
    const formatBtn = document.getElementById('formatBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const submitPasswordBtn = document.getElementById('submitPasswordBtn');

    if (executeBtn) executeBtn.addEventListener('click', executeQuery);
    if (clearBtn) clearBtn.addEventListener('click', clearResult);
    if (formatBtn) formatBtn.addEventListener('click', formatQueryAndSet);
    if (downloadBtn) downloadBtn.addEventListener('click', downloadCSV);
    if (submitPasswordBtn) {
        submitPasswordBtn.addEventListener('click', () => {
            if (currentPasswordCallback) currentPasswordCallback();
        });
    }
}

/**
 * Инициализация переключения между древовидным и текстовым представлением EXPLAIN
 */
function initExplainToggle() {
    document.addEventListener('click', function(e) {
        const btn = e.target.closest('.explain-view-btn');
        if (!btn) return;

        const view = btn.getAttribute('data-view');
        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');

        if (!treeView || !textView) return;

        // Скрываем все активные кнопки
        document.querySelectorAll('.explain-view-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        // Показываем выбранное представление
        if (view === 'tree') {
            treeView.style.display = 'block';
            textView.style.display = 'none';
        } else if (view === 'text') {
            treeView.style.display = 'none';
            textView.style.display = 'block';
        }
    });
}

/**
 * Инициализация навигации: управление кнопками "Панель преподавателя" и "Выйти"
 *
 * Логика отображения:
 * - Неавторизованный пользователь (студент): видит только кнопку "Панель преподавателя"
 * - Авторизованный преподаватель: видит обе кнопки
 * - После выхода: сессия уничтожается, кнопка "Выйти" скрывается
 */
function initNavigation() {
    const teacherLinkBtn = document.getElementById('teacherLinkBtn');
    const logoutBtn = document.getElementById('logoutBtn');

    // Если кнопок нет на странице — выходим
    if (!teacherLinkBtn && !logoutBtn) return;

    // Функция обновления видимости кнопок
    function updateButtonsVisibility(isAuthenticated) {
        if (teacherLinkBtn) {
            // Кнопка "Панель преподавателя" видна всегда (и студентам, и преподавателям)
            teacherLinkBtn.style.display = 'inline-block';
        }

        if (logoutBtn) {
            // Кнопка "Выйти" видна ТОЛЬКО авторизованному преподавателю
            logoutBtn.style.display = isAuthenticated ? 'inline-block' : 'none';
        }
    }

    // Проверяем статус аутентификации через GET /api/login
    fetch('/api/login', {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(response => response.json())
    .then(data => {
        updateButtonsVisibility(data.authenticated === true);
    })
    .catch(error => {
        console.error('Auth check failed:', error);
        // При ошибке считаем пользователя неавторизованным
        updateButtonsVisibility(false);
    });

    // Обработчик кнопки "Выйти"
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async (e) => {
            e.preventDefault();

            try {
                await fetch('/api/logout', { method: 'POST' });
                // После успешного выхода скрываем кнопку "Выйти"
                if (logoutBtn) logoutBtn.style.display = 'none';
                // Перезагружаем страницу, чтобы обновить состояние
                window.location.href = '/index';
            } catch (error) {
                console.error('Logout failed:', error);
                // Даже при ошибке пытаемся перезагрузить страницу
                window.location.href = '/index';
            }
        });
    }
}

// Запуск инициализации после загрузки DOM
document.addEventListener('DOMContentLoaded', initPage);

// ============================================
// ВИЗУАЛИЗАЦИЯ EXPLAIN
// ============================================

function renderExplainTree(explainData) {
    if (!explainData) return '<div class="empty-state">Нет данных EXPLAIN</div>';

    let planData;
    if (typeof explainData === 'string') {
        if (explainData.trim().startsWith('[') || explainData.trim().startsWith('{')) {
            try {
                planData = JSON.parse(explainData);
            } catch (e) {
                return `<pre class="explain-output">${escapeHtml(explainData)}</pre>`;
            }
        } else {
            return `<pre class="explain-output">${escapeHtml(explainData)}</pre>`;
        }
    } else {
        planData = explainData;
    }

    let plan = null;
    if (Array.isArray(planData) && planData.length > 0) {
        plan = planData[0].Plan;
    } else if (planData.Plan) {
        plan = planData.Plan;
    } else if (planData[0] && planData[0].Plan) {
        plan = planData[0].Plan;
    }

    if (!plan) {
        return `<pre class="explain-output">${escapeHtml(JSON.stringify(planData, null, 2))}</pre>`;
    }

    let html = '<div class="explain-tree">';
    html += generateSummaryStats(plan);
    html += renderPlanNode(plan);
    html += '</div>';

    return html;
}

function generateSummaryStats(plan) {
    const totalCost = plan['Total Cost'] || 'N/A';
    const actualTime = plan['Actual Total Time'] || 'N/A';
    const actualRows = plan['Actual Rows'] || 'N/A';

    return `
        <div class="explain-summary-stats">
            <div class="stat">
                <span class="stat-label">💰 Общая стоимость:</span>
                <span class="stat-value">${totalCost}</span>
            </div>
            <div class="stat">
                <span class="stat-label">⏱️ Фактическое время:</span>
                <span class="stat-value">${actualTime} ms</span>
            </div>
            <div class="stat">
                <span class="stat-label">📊 Строк обработано:</span>
                <span class="stat-value">${typeof actualRows === 'number' ? actualRows.toLocaleString() : actualRows}</span>
            </div>
        </div>
    `;
}

function renderPlanNode(node, level = 0) {
    if (!node) return '';

    const nodeType = node['Node Type'] || 'Unknown';
    const relationName = node['Relation Name'] || '';
    const actualRows = node['Actual Rows'] || node['Plan Rows'] || 0;
    const actualTime = node['Actual Total Time'] || node['Total Cost'] || 0;
    const actualLoops = node['Actual Loops'] || 1;

    let nodeClass = '';
    let icon = '📄';

    const typeLower = nodeType.toLowerCase();
    if (typeLower.includes('index')) {
        nodeClass = 'explain-node-type-index';
        icon = '🌲';
    } else if (typeLower.includes('seq scan')) {
        nodeClass = 'explain-node-type-seq';
        icon = '📊';
    } else if (typeLower.includes('join')) {
        nodeClass = 'explain-node-type-join';
        icon = '🔗';
    } else if (typeLower.includes('aggregate') || typeLower.includes('group')) {
        nodeClass = 'explain-node-type-aggregate';
        icon = '📈';
    } else if (typeLower.includes('sort')) {
        nodeClass = 'explain-node-type-sort';
        icon = '🔽';
    } else if (typeLower.includes('limit')) {
        nodeClass = 'explain-node-type-limit';
        icon = '✂️';
    }

    const nodeId = `node_${Date.now()}_${Math.random().toString(36).substr(2, 8)}`;
    const hasChildren = node.Plans && node.Plans.length > 0;

    let html = `
        <div class="explain-node ${nodeClass}" data-node-type="${nodeType}">
            <div class="explain-node-header" onclick="toggleExplainNode('${nodeId}')">
                ${hasChildren ? `<span class="explain-node-toggle" id="toggle_${nodeId}">▼</span>` : '<span class="explain-node-toggle" style="visibility: hidden;">▼</span>'}
                <span class="explain-node-icon">${icon}</span>
                <span class="explain-node-name">${escapeHtml(nodeType)}</span>
                ${relationName ? `<span class="explain-node-relation">(${escapeHtml(relationName)})</span>` : ''}
            </div>
            <div class="explain-node-stats">
                <span>📊 ${typeof actualRows === 'number' ? actualRows.toLocaleString() : actualRows} rows</span>
                <span>⏱️ ${typeof actualTime === 'number' ? actualTime.toFixed(2) : actualTime} ms</span>
                ${actualLoops > 1 ? `<span>🔄 loops: ${actualLoops}</span>` : ''}
                ${node.Condition ? `<span>🔍 condition: ${escapeHtml(node.Condition.substring(0, 50))}${node.Condition.length > 50 ? '...' : ''}</span>` : ''}
            </div>
    `;

    if (hasChildren) {
        html += `<div class="explain-node-children" id="children_${nodeId}">`;
        for (const child of node.Plans) {
            html += renderPlanNode(child, level + 1);
        }
        html += `</div>`;
    }

    html += `</div>`;

    if (!window.toggleExplainNodeFunctions) {
        window.toggleExplainNodeFunctions = {};
    }
    window.toggleExplainNodeFunctions[nodeId] = function() {
        const childrenDiv = document.getElementById(`children_${nodeId}`);
        const toggleSpan = document.getElementById(`toggle_${nodeId}`);
        if (childrenDiv) {
            if (childrenDiv.style.display === 'none') {
                childrenDiv.style.display = 'block';
                if (toggleSpan) toggleSpan.textContent = '▼';
            } else {
                childrenDiv.style.display = 'none';
                if (toggleSpan) toggleSpan.textContent = '▶';
            }
        }
    };

    return html;
}

function toggleExplainNode(nodeId) {
    if (window.toggleExplainNodeFunctions && window.toggleExplainNodeFunctions[nodeId]) {
        window.toggleExplainNodeFunctions[nodeId]();
    }
}