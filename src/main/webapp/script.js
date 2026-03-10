document.addEventListener('DOMContentLoaded', function() {
    const executeBtn = document.getElementById('executeBtn');
    const clearBtn = document.getElementById('clearBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const sqlQuery = document.getElementById('sqlQuery');
    const resultContainer = document.getElementById('resultContainer');
    const executionTime = document.getElementById('executionTime');
    const rowCount = document.getElementById('rowCount');
    const explainContainer = document.getElementById('explainContainer');
    const dbSelector = document.getElementById('dbSelector');

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

    if (downloadBtn) {
        downloadBtn.addEventListener('click', function() {
            downloadCSV();
        });
    }

    // Загружаем список доступных баз
    loadDatabases();

    function loadDatabases() {
        fetch('/api/databases')
            .then(response => response.json())
            .then(data => {
                if (data.success && data.databases && data.databases.length > 0) {
                    // Очищаем селект
                    dbSelector.innerHTML = '<option value="">Выберите базу данных</option>';

                    // Добавляем базы в селект
                    data.databases.forEach(db => {
                        const option = document.createElement('option');
                        option.value = db;
                        option.textContent = db;
                        dbSelector.appendChild(option);
                    });

                    // Если есть сохраненная база в localStorage, выбираем её
                    const savedDb = localStorage.getItem('selectedDatabase');
                    if (savedDb && data.databases.includes(savedDb)) {
                        dbSelector.value = savedDb;
                        changeDatabase(savedDb);
                    } else if (data.databases.length > 0) {
                        // Иначе выбираем первую базу
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

    function executeQuery() {
        const selectedDb = dbSelector.value;
        if (!selectedDb) {
            alert('Выберите базу данных');
            return;
        }

        const query = sqlQuery.value;
        if (!query.trim()) {
            alert('Введите SQL запрос');
            return;
        }

        resultContainer.innerHTML = '<div class="empty-state">⏳ Выполнение запроса...</div>';

        fetch('/api/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                'database': selectedDb,
                'query': query
            })
        })
        .then(response => response.json())
        .then(data => {
            displayResults(data);
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

        executionTime.textContent = `⏱️ Время: ${data.executionTime} мс`;
        rowCount.textContent = `${data.rows.length} строк`;

        if (!data.rows || data.rows.length === 0) {
            resultContainer.innerHTML = '<div class="empty-state">Запрос выполнен, но не вернул данных</div>';
            return;
        }

        let tableHTML = '<table><thead><tr>';
        data.columns.forEach(col => {
            tableHTML += `<th>${col}</th>`;
        });
        tableHTML += '</tr></thead><tbody>';

        data.rows.forEach(row => {
            tableHTML += '<tr>';
            data.columns.forEach(col => {
                let value = row[col];
                if (value === null) value = 'NULL';
                else if (typeof value === 'object') value = JSON.stringify(value);
                tableHTML += `<td>${value}</td>`;
            });
            tableHTML += '</tr>';
        });
        tableHTML += '</tbody></table>';

        resultContainer.innerHTML = tableHTML;

        if (data.explain) {
            explainContainer.innerHTML = data.explain;
        }
    }

    // Функция для генерации простой подписи (ЭЦП)
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

    // Функция скачивания результатов в CSV с подписью
    function downloadCSV() {
        const selectedDb = dbSelector.value;
        if (!selectedDb) {
            alert('Выберите базу данных');
            return;
        }

        const query = sqlQuery.value;
        if (!query.trim()) {
            alert('Введите SQL запрос');
            return;
        }

        // Показываем индикатор загрузки
        resultContainer.innerHTML = '<div class="empty-state">⏳ Подготовка файла...</div>';

        fetch('/api/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                'database': selectedDb,
                'query': query
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data.error) {
                resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка: ${data.error}</div>`;
                return;
            }

            // Генерируем подпись
            const signature = generateSignature(query, data.executionTime, data.rows.length, selectedDb);

            // Формируем CSV с метаданными и подписью
            let csv = '';

            // Метаданные (каждая строка начинается с #)
            csv += `# SQL Query: ${query}\n`;
            csv += `# Database: ${selectedDb}\n`;
            csv += `# Execution Time: ${data.executionTime} ms\n`;
            csv += `# Rows: ${data.rows.length}\n`;
            csv += `# Generated: ${new Date().toLocaleString()}\n`;
            csv += `# Signature: ${signature}\n`;
            csv += '\n'; // Пустая строка для разделения

            // Заголовки колонок
            csv += data.columns.join(',') + '\n';

            // Данные
            data.rows.forEach(row => {
                const rowData = data.columns.map(col => {
                    let value = row[col];
                    if (value === null) return 'NULL';
                    if (typeof value === 'string') {
                        // Экранируем кавычки и оборачиваем в кавычки если есть запятые
                        if (value.includes(',') || value.includes('"')) {
                            return `"${value.replace(/"/g, '""')}"`;
                        }
                    }
                    return value;
                });
                csv += rowData.join(',') + '\n';
            });

            // Создаем и скачиваем файл
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);

            link.setAttribute('href', url);
            link.setAttribute('download', `query_${selectedDb}_${timestamp}.csv`);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            // Восстанавливаем отображение результата
            displayResults(data);
        })
        .catch(error => {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        });
    }

    function clearResult() {
        if (sqlQuery) sqlQuery.value = 'SELECT * FROM student LIMIT 10;';
        if (resultContainer) {
            resultContainer.innerHTML = '<div class="empty-state">Выберите базу данных и выполните запрос</div>';
        }
        if (executionTime) executionTime.textContent = '';
        if (rowCount) rowCount.textContent = '';
        if (explainContainer) {
            explainContainer.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
        }
    }
});

// Функция загрузки информации о базе данных
function loadDbInfo(dbName) {
    if (!dbName) return;

    fetch('/api/dbinfo?db=' + encodeURIComponent(dbName))
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                document.getElementById('dbName').textContent = data.dbName;
                document.getElementById('currentDbBadge').textContent = data.dbName;
                document.getElementById('dbInfoCard').style.display = 'block';

                const tablesList = document.getElementById('tablesList');
                tablesList.innerHTML = '';

                if (data.tables && data.tables.length > 0) {
                    data.tables.sort().forEach(table => {
                        const li = document.createElement('li');
                        li.textContent = table;
                        tablesList.appendChild(li);
                    });
                } else {
                    tablesList.innerHTML = '<li style="grid-column: 1/-1; text-align: center;">Нет таблиц</li>';
                }
            } else {
                document.getElementById('dbInfoCard').style.display = 'none';
                document.getElementById('tablesList').innerHTML =
                    `<li style="grid-column: 1/-1; color: #ef4444; text-align: center;">
                        ❌ Ошибка: ${data.error}
                    </li>`;
            }
        })
        .catch(error => {
            document.getElementById('tablesList').innerHTML =
                '<li style="grid-column: 1/-1; color: #ef4444; text-align: center;">❌ Ошибка соединения</li>';
        });
}

// Функция смены базы данных
function changeDatabase(dbName) {
    if (!dbName) {
        document.getElementById('dbInfoCard').style.display = 'none';
        return;
    }

    // Сохраняем выбор в localStorage
    localStorage.setItem('selectedDatabase', dbName);

    // Загружаем информацию о новой базе
    loadDbInfo(dbName);

    // Очищаем результаты предыдущих запросов
    document.getElementById('resultContainer').innerHTML =
        '<div class="empty-state">Выберите базу данных и выполните запрос</div>';
    document.getElementById('executionTime').textContent = '';
    document.getElementById('rowCount').textContent = '';
    document.getElementById('explainContainer').innerHTML =
        '-- Здесь появится вывод EXPLAIN ANALYZE';
}