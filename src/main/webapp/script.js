document.addEventListener('DOMContentLoaded', function() {

    const executeBtn = document.getElementById('executeBtn');
    const clearBtn = document.getElementById('clearBtn');
    const sqlQuery = document.getElementById('sqlQuery');
    const resultContainer = document.getElementById('resultContainer');
    const executionTime = document.getElementById('executionTime');
    const rowCount = document.getElementById('rowCount');
    const explainContainer = document.getElementById('explainContainer');

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

    // Загружаем информацию о базе данных при старте
    loadDbInfo();

    function executeQuery() {
        const query = sqlQuery.value;
        const dbName = 'sql_tutor_university_db';  // явно указываем имя базы

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
                'database': dbName,  // передаём имя базы
                'query': query
            })
        })
        .then(response => response.json())
        .then(data => {
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
        })
        .catch(error => {
            resultContainer.innerHTML = `<div class="empty-state" style="color: #ef4444;">❌ Ошибка соединения: ${error}</div>`;
        });
    }

    function clearResult() {
        if (sqlQuery) sqlQuery.value = '';
        if (resultContainer) {
            resultContainer.innerHTML = '<div class="empty-state">Нажмите "Выполнить", чтобы увидеть результат</div>';
        }
        if (executionTime) executionTime.textContent = '';
        if (rowCount) rowCount.textContent = '';
        if (explainContainer) {
            explainContainer.innerHTML = '-- Здесь появится вывод EXPLAIN ANALYZE';
        }
    }
});

// Функция загрузки информации о базе данных
function loadDbInfo() {
    const dbName = 'sql_tutor_university_db';

    fetch('/api/dbinfo?db=' + encodeURIComponent(dbName))
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                document.getElementById('dbName').textContent = data.dbName;

                const tablesList = document.getElementById('tablesList');
                tablesList.innerHTML = '';

                data.tables.sort().forEach(table => {
                    const li = document.createElement('li');
                    li.textContent = table;
                    tablesList.appendChild(li);
                });
            } else {
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