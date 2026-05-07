/**
 * SQL Trainer - Панель преподавателя
 * Все JavaScript функции вынесены в отдельный файл для избежания проблем с компиляцией JSP
 */

// Экранирование HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Проверка аутентификации
(async function checkAuth() {
    try {
        const response = await fetch('/api/login');
        const data = await response.json();
        if (!data.authenticated) {
            window.location.href = '/login';
        }
    } catch (e) {
        window.location.href = '/login';
    }
})();

// Выход
document.getElementById('logoutBtn').addEventListener('click', async (e) => {
    e.preventDefault();
    await fetch('/api/logout', { method: 'POST' });
    window.location.href = '/login';
});

// Обновление метки файла для SQL
function updateFileLabel(input) {
    const fileNameSpan = document.getElementById('fileName');
    const fileUpload = document.querySelector('#uploadForm .file-upload');
    if (input.files && input.files.length > 0) {
        const fileName = input.files[0].name;
        const fileSize = (input.files[0].size / 1024).toFixed(0);
        if (fileSize > 1024) {
            fileNameSpan.textContent = '📄 ' + fileName + ' (' + (fileSize/1024).toFixed(2) + ' MB)';
        } else {
            fileNameSpan.textContent = '📄 ' + fileName + ' (' + fileSize + ' KB)';
        }
        fileUpload.style.borderColor = 'var(--primary)';
        fileUpload.style.background = 'var(--primary-light)';
    } else {
        fileNameSpan.textContent = 'Выберите SQL файл';
        fileUpload.style.borderColor = 'var(--border)';
        fileUpload.style.background = 'var(--light)';
    }
}

// Обновление метки файла для схемы
function updateSchemaFileLabel(input) {
    const fileNameSpan = document.getElementById('schemaFileName');
    const fileUpload = document.querySelector('#uploadSchemaForm .file-upload');
    if (input.files && input.files.length > 0) {
        const fileName = input.files[0].name;
        const fileSize = (input.files[0].size / 1024).toFixed(0);
        if (fileSize > 1024) {
            fileNameSpan.textContent = '🖼️ ' + fileName + ' (' + (fileSize/1024).toFixed(2) + ' MB)';
        } else {
            fileNameSpan.textContent = '🖼️ ' + fileName + ' (' + fileSize + ' KB)';
        }
        fileUpload.style.borderColor = 'var(--primary)';
        fileUpload.style.background = 'var(--primary-light)';
    } else {
        fileNameSpan.textContent = 'Выберите файл схемы';
        fileUpload.style.borderColor = 'var(--border)';
        fileUpload.style.background = 'var(--light)';
    }
}

// Добавление лога
function addLogMessage(message, type) {
    const logMessages = document.getElementById('logMessages');
    const logEntry = document.createElement('div');
    logEntry.className = type || 'info';
    const time = new Date().toLocaleTimeString();
    logEntry.textContent = '[' + time + '] ' + message;
    logMessages.appendChild(logEntry);
    logMessages.scrollTop = logMessages.scrollHeight;
}

// ============================================
// РАБОТА С ПАПКАМИ
// ============================================

// Загрузка папок для селектора
async function loadFoldersForSelect() {
    try {
        const response = await fetch('/api/teacher?action=listFolders');
        const data = await response.json();
        const select = document.getElementById('folderSelect');
        select.innerHTML = '<option value="">Выберите папку</option>';
        if (data.folders) {
            for (const folder of data.folders) {
                const option = document.createElement('option');
                option.value = folder.id;
                option.textContent = folder.name;
                select.appendChild(option);
            }
        }
    } catch (e) {
        console.error('Failed to load folders:', e);
    }
}

// Загрузка списка папок для отображения
async function loadFoldersList() {
    try {
        const response = await fetch('/api/teacher?action=listFolders');
        const data = await response.json();
        const container = document.getElementById('foldersList');

        if (data.folders && data.folders.length > 0) {
            let html = '<div class="folders-list">';
            for (const folder of data.folders) {
                html += `
                    <div class="folder-item">
                        <div class="folder-header" onclick="toggleFolder(${folder.id})">
                            <span class="folder-name">📁 ${escapeHtml(folder.name)}</span>
                            <span class="folder-toggle">▼</span>
                        </div>
                        <div class="folder-databases" id="folder-dbs-${folder.id}">
                            <div class="empty-state">Загрузка баз...</div>
                        </div>
                    </div>
                `;
            }
            html += '</div>';
            container.innerHTML = html;

            for (const folder of data.folders) {
                loadDatabasesForFolder(folder.id);
            }
        } else {
            container.innerHTML = '<div class="empty-state">Нет папок. Создайте первую папку.</div>';
        }
    } catch (e) {
        document.getElementById('foldersList').innerHTML = '<div class="empty-state">Ошибка загрузки</div>';
    }
}

// Загрузка баз для конкретной папки
async function loadDatabasesForFolder(folderId) {
    try {
        const response = await fetch('/api/teacher?action=list');
        const data = await response.json();
        const container = document.getElementById(`folder-dbs-${folderId}`);

        if (container) {
            const folderDatabases = data.databases ? data.databases.filter(db => db.folderId === folderId) : [];
            if (folderDatabases.length > 0) {
                let html = '';
                for (const db of folderDatabases) {
                    const isVisible = (db.isVisible !== undefined && db.isVisible !== null) ? db.isVisible : true;

                    html += `
                        <div class="folder-db-item">
                            <div>
                                <span class="folder-db-name">${escapeHtml(db.dbName)}</span>
                                <span class="folder-db-display">(${escapeHtml(db.displayName)})</span>
                            </div>
                            <div class="db-actions">
                                <button class="btn-edit" onclick="editDatabase('${escapeHtml(db.dbName)}', '${escapeHtml(db.displayName)}', ${db.folderId || 0}, ${isVisible}, '${escapeHtml(db.accessStart || '')}', '${escapeHtml(db.accessEnd || '')}')">✏️ Редактировать</button>
                                <button class="btn-delete" onclick="deleteDatabase('${escapeHtml(db.dbName)}')">Удалить</button>
                            </div>
                        </div>
                    `;
                }
                container.innerHTML = html;
            } else {
                container.innerHTML = '<div class="empty-state">Нет баз в этой папке</div>';
            }
        }
    } catch (e) {
        console.error('Failed to load databases for folder:', e);
    }
}

// Переключение отображения папки
window.toggleFolder = function(folderId) {
    const dbsContainer = document.getElementById(`folder-dbs-${folderId}`);
    const toggle = dbsContainer.previousElementSibling.querySelector('.folder-toggle');
    if (dbsContainer.classList.contains('open')) {
        dbsContainer.classList.remove('open');
        toggle.textContent = '▼';
    } else {
        dbsContainer.classList.add('open');
        toggle.textContent = '▲';
    }
};

// Создание папки
document.getElementById('createFolderBtn').addEventListener('click', async () => {
    const folderName = document.getElementById('newFolderName').value.trim();
    if (!folderName) {
        alert('Введите название папки');
        return;
    }

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'createFolder', folderName: folderName })
        });
        const data = await response.json();
        if (data.success) {
            document.getElementById('newFolderName').value = '';
            loadFoldersList();
            loadFoldersForSelect();
            alert('Папка создана');
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
});

// ============================================
// РАБОТА С БАЗАМИ ДАННЫХ
// ============================================

// Загрузка списка баз данных для управления
async function loadDatabasesList() {
    try {
        const response = await fetch('/api/teacher?action=list');
        const data = await response.json();
        const container = document.getElementById('databasesList');

        if (data.databases && data.databases.length > 0) {
            let html = '';
            for (const db of data.databases) {
                const isVisible = (db.isVisible !== undefined && db.isVisible !== null) ? db.isVisible : true;

                html += `
                    <div class="db-manager-item">
                        <div class="db-info">
                            <span class="db-name">${escapeHtml(db.dbName)}</span>
                            <span class="db-meta">${escapeHtml(db.displayName)}</span>
                            <span class="db-meta">📁 ${escapeHtml(db.folderName)}</span>
                            ${db.hasPassword ? '<span class="db-meta">🔒 Защищена паролем</span>' : ''}
                        </div>
                        <div class="db-actions">
                            <button class="btn-edit" onclick="editDatabase('${escapeHtml(db.dbName)}', '${escapeHtml(db.displayName)}', ${db.folderId || 0}, ${isVisible}, '${escapeHtml(db.accessStart || '')}', '${escapeHtml(db.accessEnd || '')}')">✏️ Редактировать</button>
                            <button class="btn-delete" onclick="deleteDatabase('${escapeHtml(db.dbName)}')">Удалить</button>
                        </div>
                    </div>
                `;
            }
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="empty-state">Нет баз данных</div>';
        }
    } catch (e) {
        document.getElementById('databasesList').innerHTML = '<div class="empty-state">Ошибка загрузки</div>';
    }
}

// Загрузка списка баз для селектора схемы
async function loadDatabasesForSchemaSelect() {
    try {
        const response = await fetch('/api/teacher?action=list');
        const data = await response.json();
        const select = document.getElementById('schemaDbSelect');
        select.innerHTML = '<option value="">Выберите базу</option>';
        if (data.databases) {
            for (const db of data.databases) {
                const option = document.createElement('option');
                option.value = db.dbName;
                option.textContent = db.displayName + ' (' + db.dbName + ')';
                option.dataset.schemaUrl = db.schemaImageUrl || '';
                select.appendChild(option);
            }
        }
    } catch (e) {
        console.error('Failed to load databases for schema select:', e);
    }
}

// Показ предпросмотра схемы при выборе базы
document.getElementById('schemaDbSelect')?.addEventListener('change', function() {
    const selectedOption = this.options[this.selectedIndex];
    const schemaUrl = selectedOption.dataset.schemaUrl;
    const previewContainer = document.getElementById('schemaPreview');
    const previewImg = document.getElementById('schemaPreviewImg');
    if (schemaUrl && schemaUrl !== 'null' && schemaUrl !== '') {
        previewImg.src = schemaUrl;
        previewContainer.style.display = 'block';
    } else {
        previewContainer.style.display = 'none';
    }
});

// Загрузка схемы
document.getElementById('uploadSchemaForm')?.addEventListener('submit', async function(e) {
    e.preventDefault();

    const dbName = document.getElementById('schemaDbSelect').value;
    const fileInput = document.getElementById('schemaImage');
    const file = fileInput.files[0];

    if (!dbName) {
        alert('Выберите базу данных');
        return;
    }
    if (!file) {
        alert('Выберите файл изображения');
        return;
    }

    const formData = new FormData();
    formData.append('action', 'uploadSchema');
    formData.append('dbName', dbName);
    formData.append('schemaImage', file);

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        if (data.success) {
            alert('Схема успешно загружена');
            loadDatabasesList();
            loadFoldersList();
            loadDatabasesForSchemaSelect();
            fileInput.value = '';
            updateSchemaFileLabel(fileInput);
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
});

// Удаление базы данных
window.deleteDatabase = async function(dbName) {
    if (!confirm('Удалить базу данных "' + dbName + '"? Это действие необратимо.')) return;

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'delete', dbName: dbName })
        });
        const data = await response.json();
        if (data.success) {
            alert('База данных удалена');
            loadDatabasesList();
            loadFoldersList();
            loadFoldersForSelect();
            loadDatabasesForSchemaSelect();
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
};

// ============================================
// РЕДАКТИРОВАНИЕ БАЗЫ ДАННЫХ
// ============================================

// Открытие модального окна редактирования
window.editDatabase = function(dbName, displayName, folderId, isVisible, accessStart, accessEnd) {
    document.getElementById('editDbName').value = dbName;
    document.getElementById('editDisplayName').value = displayName || '';
    document.getElementById('editAccessPassword').value = '';
    document.getElementById('editRemovePasswordCheckbox').checked = false;

    const visibleValue = (isVisible !== undefined && isVisible !== null) ? isVisible : true;
    document.getElementById('editIsVisible').value = visibleValue ? 'true' : 'false';

    document.getElementById('editAccessStart').value = accessStart || '';
    document.getElementById('editAccessEnd').value = accessEnd || '';

    fetch('/api/teacher?action=listFolders')
        .then(response => response.json())
        .then(data => {
            const select = document.getElementById('editFolderId');
            select.innerHTML = '';
            if (data.folders && data.folders.length > 0) {
                for (const folder of data.folders) {
                    const option = document.createElement('option');
                    option.value = folder.id;
                    option.textContent = folder.name;
                    if (folder.id == folderId) option.selected = true;
                    select.appendChild(option);
                }
            } else {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'Нет доступных папок';
                select.appendChild(option);
            }
            document.getElementById('editDatabaseModal').style.display = 'flex';
        })
        .catch(error => {
            console.error('Failed to load folders:', error);
            alert('Ошибка загрузки списка папок');
        });
};

// Закрытие модального окна
function closeEditModal() {
    document.getElementById('editDatabaseModal').style.display = 'none';
}

// Сохранение метаданных базы данных
async function saveDatabaseMetadata() {
    const dbName = document.getElementById('editDbName').value;
    const displayName = document.getElementById('editDisplayName').value;
    const folderId = document.getElementById('editFolderId').value;
    const accessPassword = document.getElementById('editAccessPassword').value;
    const removePassword = document.getElementById('editRemovePasswordCheckbox').checked;
    const isVisible = document.getElementById('editIsVisible').value;
    const accessStart = document.getElementById('editAccessStart').value;
    const accessEnd = document.getElementById('editAccessEnd').value;

    const formData = new URLSearchParams();
    formData.append('action', 'updateDatabaseMetadata');
    formData.append('dbName', dbName);
    if (displayName) formData.append('displayName', displayName);
    if (folderId) formData.append('folderId', folderId);

    if (removePassword) {
        formData.append('removePassword', 'true');
    } else if (accessPassword) {
        formData.append('accessPassword', accessPassword);
    }

    formData.append('isVisible', isVisible);
    if (accessStart) formData.append('accessStart', accessStart);
    if (accessEnd) formData.append('accessEnd', accessEnd);

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData
        });
        const data = await response.json();
        if (data.success) {
            alert('Метаданные обновлены');
            closeEditModal();
            loadDatabasesList();
            loadFoldersList();
            loadFoldersForSelect();
            loadDatabasesForSchemaSelect();
            loadDatabasesForMoodleSelect();
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
}

// ============================================
// МОНИТОРИНГ СЕССИЙ
// ============================================

// Загрузка сессий
async function loadSessions() {
    try {
        const response = await fetch('/api/teacher?action=sessions');
        const data = await response.json();
        const container = document.getElementById('sessionsList');

        if (data.sessions && data.sessions.length > 0) {
            let html = '<table class="sessions-table"><thead><tr>';
            html += '<th>IP адрес</th>';
            html += '<th>База данных</th>';
            html += '<th>Последний запрос</th>';
            html += '<th>Время выполнения</th>';
            html += '<th>Время запроса</th>';
            html += '<th>Статус</th>';
            html += '<th>Действия</th>';
            html += '</tr></thead><tbody>';

            for (const s of data.sessions) {
                const time = new Date(s.lastAccess).toLocaleTimeString();
                const shortQuery = s.lastQuery && s.lastQuery.length > 50 ? s.lastQuery.substring(0, 50) + '...' : (s.lastQuery || '—');
                const statusClass = s.blocked ? 'status-blocked' : 'status-active';
                const statusText = s.blocked ? '🔴 Заблокирована' : '🟢 Активна';

                html += `<tr>
                    <td>${escapeHtml(s.ipAddress || '—')}</td>
                    <td>${escapeHtml(s.dbName || '—')}</td>
                    <td title="${escapeHtml(s.lastQuery || '')}">${escapeHtml(shortQuery)}</td>
                    <td>${s.lastQueryTimeMs || 0} ms</td>
                    <td>${time}</td>
                    <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                    <td>
                        ${!s.blocked ?
                            `<button class="btn-terminate" onclick="terminateSession('${s.sessionId}')">🔒 Завершить</button>` :
                            `<button class="btn-terminate" style="background: var(--success); border-color: var(--success);" onclick="unblockSession('${s.sessionId}')">🔓 Разблокировать</button>`
                        }
                    </td>
                </tr>`;
            }
            html += '</tbody></table>';
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="empty-state">Нет активных сессий</div>';
        }
    } catch (e) {
        document.getElementById('sessionsList').innerHTML = '<div class="empty-state">Ошибка загрузки сессий</div>';
    }
}

// Завершение сессии (блокировка)
window.terminateSession = async function(sessionId) {
    if (!confirm('Заблокировать сессию студента?')) return;

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'terminateSession', sessionId: sessionId })
        });
        const data = await response.json();
        if (data.success) {
            alert(data.message);
            loadSessions();
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
};

// Разблокировка сессии
window.unblockSession = async function(sessionId) {
    if (!confirm('Разблокировать сессию студента?')) return;

    try {
        const response = await fetch('/api/teacher', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'unblockSession', sessionId: sessionId })
        });
        const data = await response.json();
        if (data.success) {
            alert(data.message);
            loadSessions();
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
};

// ============================================
// MOODLE ГЕНЕРАЦИЯ
// ============================================

// Обновление метки файла для Moodle
function updateMoodleFileLabel(input) {
    const fileNameSpan = document.getElementById('moodleFileName');
    if (!fileNameSpan) return;

    if (input.files && input.files.length > 0) {
        const fileName = input.files[0].name;
        fileNameSpan.textContent = '📄 ' + fileName;
    } else {
        fileNameSpan.textContent = 'Выберите файл (.txt)';
    }
}

// Загрузка списка баз для Moodle селектора
async function loadDatabasesForMoodleSelect() {
    const select = document.getElementById('moodleDbSelect');
    if (!select) return;

    try {
        const response = await fetch('/api/teacher?action=list');
        const data = await response.json();
        select.innerHTML = '<option value="">Выберите базу</option>';
        if (data.databases) {
            for (const db of data.databases) {
                const option = document.createElement('option');
                option.value = db.dbName;
                option.textContent = db.displayName + ' (' + db.dbName + ')';
                select.appendChild(option);
            }
        }
    } catch (e) {
        console.error('Failed to load databases for moodle select:', e);
    }
}

// Обработчик формы Moodle
const moodleForm = document.getElementById('moodleForm');
if (moodleForm) {
    moodleForm.addEventListener('submit', async function(e) {
        e.preventDefault();

        const dbName = document.getElementById('moodleDbSelect').value;
        const category = document.getElementById('moodleCategory').value;
        const format = document.getElementById('moodleFormat') ? document.getElementById('moodleFormat').value : 'gift';
        const fileInput = document.getElementById('questionsFile');
        const file = fileInput.files[0];

        if (!dbName) {
            alert('Выберите базу данных');
            return;
        }
        if (!file) {
            alert('Выберите файл с вопросами');
            return;
        }

        const formData = new FormData();
        formData.append('database', dbName);
        formData.append('category', category);
        formData.append('format', format);
        formData.append('questionsFile', file);

        const resultDiv = document.getElementById('moodleResult');
        resultDiv.style.display = 'block';
        resultDiv.innerHTML = '<div class="empty-state">⏳ Генерация...</div>';

        try {
            const response = await fetch('/api/teacher/moodle', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                let extension = '';
                if (format === 'gift') extension = '.gift';
                else if (format === 'xml') extension = '.xml';
                else extension = '.txt';
                a.download = `moodle_questions_${Date.now()}${extension}`;
                a.href = url;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);

                resultDiv.innerHTML = '<div class="empty-state" style="color: green;">✅ Файл сгенерирован и скачан</div>';
                setTimeout(() => resultDiv.style.display = 'none', 3000);
            } else {
                const error = await response.json();
                resultDiv.innerHTML = `<div class="empty-state" style="color: red;">❌ Ошибка: ${error.error || 'Неизвестная ошибка'}</div>`;
            }
        } catch (err) {
            resultDiv.innerHTML = `<div class="empty-state" style="color: red;">❌ Ошибка: ${err.message}</div>`;
        }
    });
}

// ============================================
// ЗАГРУЗКА SQL-СКРИПТА
// ============================================

document.getElementById('uploadForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const dbName = document.getElementById('dbName').value.trim();
    const folderId = document.getElementById('folderSelect').value;
    const displayName = document.getElementById('displayName').value.trim();
    const accessPassword = document.getElementById('accessPassword').value;
    const fileInput = document.getElementById('sqlFile');
    const file = fileInput.files[0];

    if (!dbName) {
        alert('Введите название базы данных');
        return;
    }
    if (!folderId) {
        alert('Выберите папку');
        return;
    }
    if (!file) {
        alert('Выберите SQL файл');
        return;
    }

    const overlay = document.getElementById('loadingOverlay');
    const progressFill = document.getElementById('progressFill');
    const loadingStatus = document.getElementById('loadingStatus');
    const logMessages = document.getElementById('logMessages');

    overlay.style.display = 'flex';
    progressFill.style.width = '10%';
    loadingStatus.textContent = 'Отправка файла...';
    logMessages.innerHTML = '';
    addLogMessage('Начало загрузки файла: ' + file.name, 'info');

    const formData = new FormData();
    formData.append('action', 'upload');
    formData.append('dbName', dbName);
    formData.append('sqlFile', file);
    if (displayName) formData.append('displayName', displayName);
    if (accessPassword) formData.append('accessPassword', accessPassword);
    if (folderId) formData.append('folderId', folderId);

    const eventSource = new EventSource('/api/logs');
    eventSource.onmessage = function(event) {
        try {
            const data = JSON.parse(event.data);
            if (data.type === 'start') {
                addLogMessage('Начало выполнения скрипта. Всего запросов: ' + data.total, 'info');
            } else if (data.type === 'progress') {
                const percent = Math.round((data.current / data.total) * 100);
                progressFill.style.width = percent + '%';
                loadingStatus.textContent = 'Выполнение: ' + percent + '% (' + data.current + '/' + data.total + ')';
                addLogMessage('▶ ' + (data.query || 'Выполнение запроса...'), 'info');
            } else if (data.type === 'success') {
                addLogMessage('✅ ' + data.message, 'success');
            } else if (data.type === 'error') {
                addLogMessage('❌ Ошибка: ' + data.error, 'error');
            } else if (data.type === 'complete') {
                addLogMessage('🎉 ' + data.message, 'success');
                setTimeout(() => eventSource.close(), 2000);
            }
        } catch (e) {
            addLogMessage(event.data, 'info');
        }
    };
    eventSource.onerror = function() {
        addLogMessage('⚠️ Соединение с сервером логов закрыто', 'warning');
        eventSource.close();
    };

    const xhr = new XMLHttpRequest();
    xhr.upload.addEventListener('progress', function(e) {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            progressFill.style.width = percent + '%';
            loadingStatus.textContent = 'Загрузка файла: ' + Math.round(percent) + '%';
        }
    });
    xhr.addEventListener('load', function() {
        if (xhr.status === 200) {
            try {
                const data = JSON.parse(xhr.responseText);
                if (data.success) {
                    progressFill.style.width = '100%';
                    loadingStatus.textContent = 'База данных успешно создана';
                    addLogMessage('✅ База данных успешно создана!', 'success');
                    setTimeout(() => {
                        overlay.style.display = 'none';

                        document.getElementById('uploadForm').reset();
                        document.getElementById('fileName').textContent = 'Выберите SQL файл';

                        const fileUpload = document.querySelector('#uploadForm .file-upload');
                        if (fileUpload) {
                            fileUpload.style.borderColor = 'var(--border)';
                            fileUpload.style.background = 'var(--light)';
                        }

                        document.getElementById('accessPassword').value = '';

                        loadDatabasesList();
                        loadFoldersList();
                        loadFoldersForSelect();
                        loadDatabasesForSchemaSelect();
                        loadDatabasesForMoodleSelect();
                    }, 2000);
                } else {
                    overlay.style.display = 'none';
                    addLogMessage('❌ Ошибка: ' + data.error, 'error');
                    alert('Ошибка: ' + data.error);
                }
            } catch (e) {
                overlay.style.display = 'none';
                alert('Ошибка при обработке ответа сервера');
            }
        } else {
            overlay.style.display = 'none';
            alert('Ошибка соединения: ' + xhr.status);
        }
    });
    xhr.addEventListener('error', function() {
        overlay.style.display = 'none';
        alert('Ошибка соединения');
    });
    xhr.open('POST', '/api/teacher', true);
    xhr.send(formData);
});

// Переключение вкладок
document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById(`tab-${tab.dataset.tab}`).classList.add('active');
    });
});

// Загрузка начальных данных
loadFoldersForSelect();
loadDatabasesList();
loadFoldersList();
loadSessions();
loadDatabasesForSchemaSelect();
loadDatabasesForMoodleSelect();
setInterval(loadSessions, 5000);