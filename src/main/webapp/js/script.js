let sqlEditor = null;
let currentDbRequest = null;
let currentDbName = null;
let currentTables = [];
let currentTablesWithColumns = {};
let sqlKeywords = [
    'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'OUTER JOIN',
    'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'AND', 'OR', 'NOT',
    'IN', 'EXISTS', 'BETWEEN', 'LIKE', 'IS NULL', 'IS NOT NULL', 'AS', 'ON',
    'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'DISTINCT', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END'
];

/**
 * Получает токен из localStorage
 */
function getAuthToken() {
    return localStorage.getItem('accessToken');
}

/**
 * Возвращает заголовки с авторизацией
 */
function getAuthHeaders() {
    const token = getAuthToken();
    return token ? { 'Authorization': 'Bearer ' + token } : {};
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', function() {
    const executeBtn = document.getElementById('executeBtn');
    const clearBtn = document.getElementById('clearBtn');
    const formatBtn = document.getElementById('formatBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const resultContainer = document.getElementById('resultContainer');
    const executionTime = document.getElementById('executionTime');
    const rowCount = document.getElementById('rowCount');
    const dbSelector = document.getElementById('dbSelector');

    // Инициализация CodeMirror
    const textarea = document.getElementById('sqlQuery');
    if (textarea) {
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
                'Ctrl-Enter': function(cm) {
                    executeQuery();
                },
                'Ctrl-Shift-F': function(cm) {
                    formatQueryAndSet();
                }
            }
        });

        sqlEditor.setOption('hintOptions', {
            hint: customSqlHint,
            completeSingle: false,
            alignWithWord: false
        });
    }

    if (executeBtn) {
        executeBtn.addEventListener('click', function() {
            executeQuery();
        });
    }

    if (clearBtn) {
        clearBtn.addEventListener('click', function() {
            clearResult();
        });
    }

    if (formatBtn) {
        formatBtn.addEventListener('click', function() {
            formatQueryAndSet();
        });
    }

    if (downloadBtn) {
        downloadBtn.addEventListener('click', function() {
            downloadCSV();
        });
    }

    // Делегирование событий для переключения вида EXPLAIN
    document.addEventListener('click', function(e) {
        const btn = e.target.closest('.explain-view-btn');
        if (!btn) return;

        const view = btn.getAttribute('data-view');
        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');

        if (!treeView || !textView) return;

        // Обновляем активную кнопку
        document.querySelectorAll('.explain-view-btn').forEach(b => {
            b.classList.remove('active');
        });
        btn.classList.add('active');

        // Показываем нужное представление
        if (view === 'tree') {
            treeView.style.display = 'block';
            textView.style.display = 'none';
        } else if (view === 'text') {
            treeView.style.display = 'none';
            textView.style.display = 'block';
        }
    });

    loadDatabases();

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

    function getErrorHint(query, errorMessage) {
        const lowerError = errorMessage.toLowerCase();
        const suggestions = [];

        if (lowerError.includes('syntax error')) {
            if (query.match(/SELECT\s+\w+\s+FROM/i) && !query.match(/SELECT\s+\w+,\s+\w+\s+FROM/i)) {
                suggestions.push('💡 Возможно, пропущена запятая между колонками. Пример: SELECT id, name FROM table');
            }
            if (query.match(/FROM\s+FROM/i)) {
                suggestions.push('💡 Обнаружено два FROM. Удалите лишний.');
            }
            if (query.match(/WHERE\s+WHERE/i)) {
                suggestions.push('💡 Обнаружено два WHERE. Удалите лишний.');
            }
            if (query.match(/SELECT\s+FROM/i)) {
                suggestions.push('💡 После SELECT нужно указать колонки. Пример: SELECT * FROM table');
            }
        }

        if (lowerError.includes('column') && lowerError.includes('does not exist')) {
            const columnMatch = errorMessage.match(/"([^"]+)"/);
            if (columnMatch) {
                const columnName = columnMatch[1];
                suggestions.push(`💡 Колонка "${columnName}" не существует. Проверьте название колонки.`);

                const allColumns = [];
                for (const table in currentTablesWithColumns) {
                    if (currentTablesWithColumns[table]) {
                        currentTablesWithColumns[table].forEach(col => {
                            allColumns.push(col);
                        });
                    }
                }
                const similar = allColumns.filter(col =>
                    col.toLowerCase().includes(columnName.toLowerCase()) ||
                    columnName.toLowerCase().includes(col.toLowerCase())
                );
                if (similar.length > 0) {
                    suggestions.push(`💡 Возможно, вы имели в виду: ${similar.slice(0, 3).join(', ')}`);
                }
            }
        }

        if (lowerError.includes('relation') && lowerError.includes('does not exist')) {
            const tableMatch = errorMessage.match(/"([^"]+)"/);
            if (tableMatch) {
                const tableName = tableMatch[1];
                suggestions.push(`💡 Таблица "${tableName}" не существует.`);
                if (currentTables.length > 0) {
                    suggestions.push(`💡 Доступные таблицы: ${currentTables.join(', ')}`);
                }
            }
        }

        if (lowerError.includes('permission denied')) {
            suggestions.push('💡 У вас нет прав на выполнение этой операции. Разрешены только SELECT запросы.');
        }

        if (lowerError.includes('timeout')) {
            suggestions.push('💡 Запрос выполняется слишком долго. Попробуйте добавить LIMIT или оптимизировать запрос.');
        }

        if (suggestions.length === 0) {
            suggestions.push('💡 Проверьте синтаксис SQL. Убедитесь, что названия таблиц и колонок написаны правильно.');
        }

        return suggestions.join('\n');
    }

    function getQueryToExecute() {
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
                const start = textarea.selectionStart;
                const end = textarea.selectionEnd;
                if (start !== end) {
                    const selected = textarea.value.substring(start, end);
                    if (selected && selected.trim().length > 0) {
                        query = selected.trim();
                    }
                } else {
                    query = textarea.value.trim();
                }
            }
        }

        return formatQuery(query);
    }

    function loadDatabases() {
        const token = getAuthToken();
        fetch('/api/databases', {
            headers: token ? { 'Authorization': 'Bearer ' + token } : {}
        })
            .then(response => {
                if (response.status === 401) {
                    window.location.href = '/login';
                    throw new Error('Unauthorized');
                }
                return response.json();
            })
            .then(data => {
                if (data.success && data.databases && data.databases.length > 0) {
                    dbSelector.innerHTML = '<option value="">Выберите базу данных</option>';

                    data.databases.forEach(db => {
                        const option = document.createElement('option');
                        option.value = db;
                        option.textContent = db;
                        dbSelector.appendChild(option);
                    });

                    const savedDb = localStorage.getItem('selectedDatabase');
                    if (savedDb && data.databases.includes(savedDb)) {
                        dbSelector.value = savedDb;
                        changeDatabase(savedDb);
                    } else if (data.databases.length > 0) {
                        dbSelector.value = data.databases[0];
                        changeDatabase(data.databases[0]);
                    }
                } else {
                    dbSelector.innerHTML = '<option value="">Нет доступных баз</option>';
                    document.getElementById('dbInfoCard').style.display = 'none';
                }
            })
            .catch(error => {
                console.error('Error loading databases:', error);
                dbSelector.innerHTML = '<option value="">Ошибка загрузки баз</option>';
            });
    }

    async function loadTableColumns(dbName, tableName) {
        try {
            const token = getAuthToken();
            const response = await fetch(`/api/columns?db=${encodeURIComponent(dbName)}&table=${encodeURIComponent(tableName)}`, {
                headers: token ? { 'Authorization': 'Bearer ' + token } : {}
            });
            const data = await response.json();
            if (data.success) {
                currentTablesWithColumns[tableName] = data.columns;
            }
        } catch (error) {
            console.error(`Failed to load columns for ${tableName}:`, error);
        }
    }

    async function loadAllTableColumns(dbName, tables) {
        currentTablesWithColumns = {};
        for (const table of tables) {
            await loadTableColumns(dbName, table);
        }
    }

    function executeQuery() {
        const selectedDb = dbSelector.value;
        if (!selectedDb) {
            alert('Выберите базу данных');
            return;
        }

        let query = getQueryToExecute();
        if (!query) {
            alert('Введите или выделите SQL запрос');
            return;
        }

        if (!query.toLowerCase().startsWith('select')) {
            alert('Для выполнения разрешены только SELECT запросы');
            return;
        }

        resultContainer.innerHTML = '<div class="empty-state">⏳ Выполнение запроса...</div>';

        // Получаем состояние чекбокса
        const showExplain = document.getElementById('showExplainCheckbox')?.checked ?? true;

        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');
        if (treeView) treeView.innerHTML = showExplain ? '-- Получение плана выполнения...' : '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
        if (textView) textView.textContent = showExplain ? '-- Получение плана выполнения...' : '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';

        const token = getAuthToken();

        // Добавляем параметр explain в запрос
        const bodyParams = new URLSearchParams({
            'database': selectedDb,
            'query': query,
            'explain': showExplain ? 'true' : 'false'  // НОВЫЙ ПАРАМЕТР
        });

        fetch('/api/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Authorization': token ? 'Bearer ' + token : ''
            },
            body: bodyParams
        })
        .then(response => {
            if (response.status === 401) {
                window.location.href = '/login';
                throw new Error('Unauthorized');
            }
            return response.json();
        })
        .then(data => {
            if (data.error) {
                const hint = getErrorHint(query, data.error);
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">
                    ❌ Ошибка: ${escapeHtml(data.error)}<br><br>
                    <span style="color: #f59e0b; font-size: 0.9rem;">${escapeHtml(hint)}</span>
                </div>`;
            } else {
                displayResults(data);
            }
        })
        .catch(error => {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        });
    }

    function displayResults(data) {
        if (data.error) {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${data.error}</div>`;
            return;
        }

        executionTime.textContent = `⏱️ Время: ${data.executionTimeMs} мс`;
        rowCount.textContent = `${data.rows.length} строк`;

        // Визуализация EXPLAIN
        const showExplain = document.getElementById('showExplainCheckbox')?.checked ?? true;

        if (!showExplain) {
            // Если EXPLAIN выключен, показываем сообщение
            const treeView = document.getElementById('explainTreeView');
            const textView = document.getElementById('explainTextView');
            if (treeView) treeView.innerHTML = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
            if (textView) textView.textContent = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
        } else if (data.explainJson || data.explainText) {
            displayExplain(data.explainJson, data.explainText);
        } else if (data.explain) {
            displayExplain(data.explain, data.explain);
        } else {
            const treeView = document.getElementById('explainTreeView');
            const textView = document.getElementById('explainTextView');
            if (treeView) treeView.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
            if (textView) textView.textContent = '-- Здесь появится вывод EXPLAIN ANALYZE';
        }

        if (!data.rows || data.rows.length === 0) {
            resultContainer.innerHTML = '<div class="empty-state">✅ Запрос выполнен, но не вернул данных</div>';
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

        resultContainer.innerHTML = tableHTML;
    }

    function displayExplain(explainData, explainText) {
        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');

        if (!treeView || !textView) {
            const explainContainer = document.getElementById('explainContainer');
            if (explainContainer) {
                explainContainer.innerHTML = renderExplainTree(explainData);
            }
            return;
        }

        // Рендерим дерево (из JSON)
        if (explainData && explainData !== explainText) {
            treeView.innerHTML = renderExplainTree(explainData);
        } else {
            treeView.innerHTML = renderExplainTree(explainText);
        }

        // Рендерим оригинальный текст PostgreSQL
        if (explainText) {
            textView.textContent = explainText;
        } else if (typeof explainData === 'string') {
            textView.textContent = explainData;
        } else {
            textView.textContent = 'Нет данных EXPLAIN';
        }

        // Устанавливаем активное представление по умолчанию (дерево)
        treeView.style.display = 'block';
        textView.style.display = 'none';

        // Обновляем активную кнопку
        const activeBtn = document.querySelector('.explain-view-btn[data-view="tree"]');
        if (activeBtn) {
            document.querySelectorAll('.explain-view-btn').forEach(b => b.classList.remove('active'));
            activeBtn.classList.add('active');
        }
    }

    function generateSignature(query, time, rows, dbName) {
        const data = query + time + rows + dbName + new Date().toDateString();
        let hash = 0;
        for (let i = 0; i < data.length; i++) {
            const char = data.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(16).padStart(8, '0');
    }

    function downloadCSV() {
        const selectedDb = dbSelector.value;
        if (!selectedDb) {
            alert('Выберите базу данных');
            return;
        }

        let query = getQueryToExecute();
        if (!query) {
            alert('Введите или выделите SQL запрос');
            return;
        }

        resultContainer.innerHTML = '<div class="empty-state">⏳ Подготовка файла...</div>';

        const token = getAuthToken();
        fetch('/api/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Authorization': token ? 'Bearer ' + token : ''
            },
            body: new URLSearchParams({
                'database': selectedDb,
                'query': query
            })
        })
        .then(response => {
            if (response.status === 401) {
                window.location.href = '/login';
                throw new Error('Unauthorized');
            }
            return response.json();
        })
        .then(data => {
            if (data.error) {
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${data.error}</div>`;
                return;
            }

            const signature = generateSignature(query, data.executionTimeMs, data.rows.length, selectedDb);

            let csv = '';
            csv += `# SQL Query: ${query.replace(/\n/g, ' ')}\n`;
            csv += `# Database: ${selectedDb}\n`;
            csv += `# Execution Time: ${data.executionTimeMs} ms\n`;
            csv += `# Rows: ${data.rows.length}\n`;
            csv += `# Generated: ${new Date().toLocaleString()}\n`;
            csv += `# Signature: ${signature}\n`;
            csv += '\n';

            csv += data.columns.join(',') + '\n';

            data.rows.forEach(row => {
                const rowData = data.columns.map(col => {
                    let value = row[col];
                    if (value === null) return 'NULL';
                    if (typeof value === 'string') {
                        if (value.includes(',') || value.includes('"') || value.includes('\n')) {
                            return `"${value.replace(/"/g, '""')}"`;
                        }
                    }
                    return value;
                });
                csv += rowData.join(',') + '\n';
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
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        });
    }

    function clearResult() {
        if (sqlEditor) {
            sqlEditor.setValue('SELECT * FROM student LIMIT 10;');
        } else if (document.getElementById('sqlQuery')) {
            document.getElementById('sqlQuery').value = 'SELECT * FROM student LIMIT 10;';
        }
        if (resultContainer) {
            resultContainer.innerHTML = '<div class="empty-state">Выберите базу данных и выполните запрос</div>';
        }
        if (executionTime) executionTime.textContent = '';
        if (rowCount) rowCount.textContent = '';

        const showExplain = document.getElementById('showExplainCheckbox')?.checked ?? true;
        const treeView = document.getElementById('explainTreeView');
        const textView = document.getElementById('explainTextView');

        if (showExplain) {
            if (treeView) treeView.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
            if (textView) textView.textContent = '-- Здесь появится вывод EXPLAIN ANALYZE';
        } else {
            if (treeView) treeView.innerHTML = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
            if (textView) textView.textContent = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
        }
    }
});

// ============================================
// ГЛОБАЛЬНЫЕ ФУНКЦИИ
// ============================================

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
    const currentDbBadge = document.getElementById('currentDbBadge');
    const dbInfoCard = document.getElementById('dbInfoCard');

    dbNameElement.textContent = dbName;
    currentDbBadge.textContent = dbName;
    currentDbBadge.title = dbName;
    dbInfoCard.style.display = 'block';

    if (tablesCountSpan) {
        tablesCountSpan.textContent = '0';
    }

    tablesList.innerHTML = '<li style="text-align: center; justify-content: center;">⏳ Загрузка таблиц...</li>';

    currentDbRequest = new AbortController();

    const token = getAuthToken();
    fetch('/api/dbinfo?db=' + encodeURIComponent(dbName), {
        signal: currentDbRequest.signal,
        headers: token ? { 'Authorization': 'Bearer ' + token } : {}
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            return response.json();
        })
        .then(async (data) => {
            if (currentDbName !== dbName) return;

            if (data.success) {
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
                    tablesList.innerHTML = '<li style="text-align: center; justify-content: center;">📭 Нет таблиц</li>';
                }
            } else {
                tablesList.innerHTML = `<li style="color: #ef4444; text-align: center; justify-content: center;">❌ ${data.error || 'Ошибка загрузки'}</li>`;
                if (tablesCountSpan) {
                    tablesCountSpan.textContent = '0';
                }
            }
            currentDbRequest = null;
        })
        .catch(error => {
            if (error.name === 'AbortError') {
                return;
            }
            console.error('Error loading db info:', error);
            if (currentDbName === dbName) {
                tablesList.innerHTML = '<li style="color: #ef4444; text-align: center; justify-content: center;">❌ Ошибка соединения</li>';
                if (tablesCountSpan) {
                    tablesCountSpan.textContent = '0';
                }
            }
            currentDbRequest = null;
        });
}

async function loadAllTableColumns(dbName, tables) {
    for (const table of tables) {
        try {
            const token = getAuthToken();
            const response = await fetch(`/api/columns?db=${encodeURIComponent(dbName)}&table=${encodeURIComponent(table)}`, {
                headers: token ? { 'Authorization': 'Bearer ' + token } : {}
            });
            const data = await response.json();
            if (data.success) {
                window.currentTablesWithColumns = window.currentTablesWithColumns || {};
                window.currentTablesWithColumns[table] = data.columns;
            }
        } catch (error) {
            console.error(`Failed to load columns for ${table}:`, error);
        }
    }
}

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

function changeDatabase(dbName) {
    if (!dbName) {
        const dbInfoCard = document.getElementById('dbInfoCard');
        if (dbInfoCard) dbInfoCard.style.display = 'none';
        return;
    }

    localStorage.setItem('selectedDatabase', dbName);

    const dbSelector = document.getElementById('dbSelector');
    if (dbSelector && dbSelector.value !== dbName) {
        dbSelector.value = dbName;
    }

    loadDbInfo(dbName);

    const resultContainer = document.getElementById('resultContainer');
    const executionTime = document.getElementById('executionTime');
    const rowCount = document.getElementById('rowCount');

    if (resultContainer) {
        resultContainer.innerHTML = '<div class="empty-state">✅ База данных изменена. Выполните запрос.</div>';
    }
    if (executionTime) executionTime.textContent = '';
    if (rowCount) rowCount.textContent = '';

    const showExplain = document.getElementById('showExplainCheckbox')?.checked ?? true;
    const treeView = document.getElementById('explainTreeView');
    const textView = document.getElementById('explainTextView');

    if (showExplain) {
        if (treeView) treeView.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
        if (textView) textView.textContent = '-- Здесь появится вывод EXPLAIN ANALYZE';
    } else {
        if (treeView) treeView.innerHTML = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
        if (textView) textView.textContent = '-- EXPLAIN отключён. Включите чекбокс для просмотра плана.';
    }
}

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