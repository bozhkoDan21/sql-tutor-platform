/**
 * SQL Trainer - Панель преподавателя
 * Управление базами данных и папками
 */

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// ============================================

/**
 * Экранирует HTML-символы для предотвращения XSS-атак
 * @param {string} text - текст для экранирования
 * @returns {string} экранированный текст
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

let csrfToken = null;

/**
 * Загружает CSRF-токен с сервера
 * @returns {Promise<boolean>} true если токен успешно загружен
 */
async function loadCsrfToken() {
    try {
        const response = await fetch('/api/csrf/token');
        const data = await response.json();
        if (data.success) {
            csrfToken = data.token;
            document.querySelectorAll('input[name="csrf_token"]').forEach(input => {
                input.value = csrfToken;
            });
            return true;
        }
    } catch (error) {
        console.error('Failed to load CSRF token:', error);
    }
    return false;
}

/**
 * Добавляет CSRF-токен к FormData
 * @param {FormData} formData - объект FormData
 * @returns {FormData} изменённый FormData
 */
function addCsrfToFormData(formData) {
    if (csrfToken) formData.append('csrf_token', csrfToken);
    return formData;
}

/**
 * Добавляет CSRF-токен к URLSearchParams
 * @param {URLSearchParams} params - объект URLSearchParams
 * @returns {URLSearchParams} изменённый URLSearchParams
 */
function addCsrfToParams(params) {
    if (csrfToken) params.append('csrf_token', csrfToken);
    return params;
}

// ============================================
// АУТЕНТИФИКАЦИЯ
// ============================================

/**
 * Проверяет аутентификацию преподавателя при загрузке страницы
 */
(async function checkAuth() {
    try {
        const response = await fetch('/api/login');
        const data = await response.json();
        if (!data.authenticated) window.location.href = '/login';
    } catch (e) {
        window.location.href = '/login';
    }
})();

/**
 * Обработчик кнопки выхода из системы
 */
const logoutBtn = document.getElementById('logoutBtn');
if (logoutBtn) {
    logoutBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        await fetch('/api/logout', { method: 'POST' });
        window.location.href = '/login';
    });
}

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ ФАЙЛОВ
// ============================================

/**
 * Обновляет метку выбранного SQL файла
 * @param {HTMLInputElement} input - элемент input file
 */
function updateFileLabel(input) {
    const fileNameSpan = document.getElementById('fileName');
    const fileUpload = document.querySelector('#uploadForm .file-upload');
    if (!fileNameSpan || !fileUpload) return;

    if (input.files && input.files.length > 0) {
        const fileName = input.files[0].name;
        const fileSize = (input.files[0].size / 1024).toFixed(0);
        fileNameSpan.textContent = fileSize > 1024
            ? `📄 ${fileName} (${(fileSize/1024).toFixed(2)} MB)`
            : `📄 ${fileName} (${fileSize} KB)`;
        fileUpload.style.borderColor = 'var(--primary)';
        fileUpload.style.background = 'var(--primary-light)';
    } else {
        fileNameSpan.textContent = 'Выберите SQL файл';
        fileUpload.style.borderColor = 'var(--border)';
        fileUpload.style.background = 'var(--light)';
    }
}

/**
 * Обновляет метку выбранного файла схемы
 * @param {HTMLInputElement} input - элемент input file
 */
function updateSchemaFileLabel(input) {
    const fileNameSpan = document.getElementById('schemaFileName');
    const fileUpload = document.querySelector('#uploadSchemaForm .file-upload');
    if (!fileNameSpan || !fileUpload) return;

    if (input.files && input.files.length > 0) {
        const fileName = input.files[0].name;
        const fileSize = (input.files[0].size / 1024).toFixed(0);
        fileNameSpan.textContent = fileSize > 1024
            ? `🖼️ ${fileName} (${(fileSize/1024).toFixed(2)} MB)`
            : `🖼️ ${fileName} (${fileSize} KB)`;
        fileUpload.style.borderColor = 'var(--primary)';
        fileUpload.style.background = 'var(--primary-light)';
    } else {
        fileNameSpan.textContent = 'Выберите файл схемы';
        fileUpload.style.borderColor = 'var(--border)';
        fileUpload.style.background = 'var(--light)';
    }
}

/**
 * Добавляет сообщение в лог-контейнер
 * @param {string} message - текст сообщения
 * @param {string} type - тип сообщения (info, success, error, warning)
 */
function addLogMessage(message, type) {
    const logMessages = document.getElementById('logMessages');
    if (!logMessages) return;

    const logEntry = document.createElement('div');
    logEntry.className = type || 'info';
    logEntry.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    logMessages.appendChild(logEntry);
    logMessages.scrollTop = logMessages.scrollHeight;
}

// ============================================
// УПРАВЛЕНИЕ ПАПКАМИ
// ============================================

/**
 * Загружает список папок для выпадающего списка (форма создания БД)
 */
async function loadFoldersForSelect() {
    try {
        const response = await fetch('/api/teacher?action=listFolders');
        const data = await response.json();
        const select = document.getElementById('folderSelect');
        if (!select) return;

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
        const select = document.getElementById('folderSelect');
        if (select) select.innerHTML = '<option value="">Ошибка загрузки папок</option>';
    }
}

/**
 * Рендерит строку базы данных для отображения в списке
 * @param {Object} db - объект базы данных
 * @param {boolean} isVisible - видимость базы
 * @param {boolean} isOrphaned - является ли база "сиротой" (папка не существует)
 * @param {boolean} noFolder - отсутствует ли папка (folder_id = NULL)
 * @returns {string} HTML-строка
 */
function renderDatabaseRow(db, isVisible, isOrphaned, noFolder) {
    let warningBadge = '';
    if (noFolder) warningBadge = '<span class="folder-db-warning">⚠️ без папки</span>';
    if (isOrphaned) warningBadge = '<span class="folder-db-error">❌ папка не найдена</span>';

    return `
        <div class="folder-db-item">
            <div>
                <span class="folder-db-name">${escapeHtml(db.dbName)}</span>
                <span class="folder-db-display">(${escapeHtml(db.displayName)})</span>
                <span class="folder-db-display">📊 лимит: ${db.maxRows || 20} строк</span>
                ${warningBadge}
            </div>
            <div class="db-actions">
                <button class="btn-move" onclick="moveDatabaseToFolder('${escapeHtml(db.dbName)}', ${db.folderId || 'null'})" title="Переместить в папку">📁</button>
                <button class="btn-edit" onclick="editDatabase('${escapeHtml(db.dbName)}', '${escapeHtml(db.displayName)}', ${db.folderId || 0}, ${isVisible}, '${escapeHtml(db.accessStart || '')}', '${escapeHtml(db.accessEnd || '')}', ${db.maxRows || 20})">✏️ Редактировать</button>
                <button class="btn-delete" onclick="deleteDatabase('${escapeHtml(db.dbName)}')">Удалить</button>
            </div>
        </div>
    `;
}

/**
 * Загружает и отображает все папки и базы данных в разделе "Существующие базы данных"
 */
async function loadFoldersList() {
    const container = document.getElementById('foldersList');
    if (!container) return;
    container.innerHTML = '<div class="empty-state">⏳ Загрузка папок...</div>';

    try {
        const [foldersRes, dbsRes] = await Promise.all([
            fetch('/api/teacher?action=listFolders'),
            fetch('/api/teacher?action=list')
        ]);

        if (!foldersRes.ok || !dbsRes.ok) throw new Error('Ошибка загрузки');

        const foldersData = await foldersRes.json();
        const dbsData = await dbsRes.json();

        // Карта существующих папок
        const foldersMap = new Map();
        if (foldersData.folders) {
            for (const folder of foldersData.folders) {
                const idStr = String(folder.id);
                foldersMap.set(idStr, {
                    id: folder.id,
                    idStr: idStr,
                    name: folder.name,
                    databases: []
                });
            }
        }

        const databasesWithoutFolder = [];
        const orphanedDatabases = [];

        if (dbsData.databases) {
            for (const db of dbsData.databases) {
                const folderId = db.folderId;
                const folderIdStr = folderId ? String(folderId) : null;

                if (!folderIdStr) {
                    databasesWithoutFolder.push(db);
                } else if (foldersMap.has(folderIdStr)) {
                    foldersMap.get(folderIdStr).databases.push(db);
                } else {
                    orphanedDatabases.push(db);
                }
            }
        }

        let html = '<div class="folders-list">';

        // Нормальные папки
        const sortedFolders = Array.from(foldersMap.values()).sort((a, b) => (a.name || '').localeCompare(b.name || ''));
        for (const folder of sortedFolders) {
            const dbCount = folder.databases.length;
            const safeId = folder.idStr.replace(/[^a-zA-Z0-9]/g, '_');

            html += `
                <div class="folder-item">
                    <div class="folder-header" onclick="toggleFolder('${safeId}')">
                        <span class="folder-name">📁 ${escapeHtml(folder.name)} ${dbCount ? `(${dbCount})` : '(пусто)'}</span>
                        <div class="folder-actions" onclick="event.stopPropagation()">
                            <button class="btn-folder-edit" onclick="editFolder(${folder.id}, '${escapeHtml(folder.name)}')" title="Редактировать">✏️</button>
                            <button class="btn-folder-delete" onclick="deleteFolder(${folder.id}, '${escapeHtml(folder.name)}', ${dbCount})" title="Удалить">🗑️</button>
                        </div>
                        <span class="folder-toggle">▼</span>
                    </div>
                    <div class="folder-databases" id="folder-dbs-${safeId}" style="display: none;">
            `;

            if (dbCount > 0) {
                for (const db of folder.databases) {
                    const isVisible = (db.isVisible !== undefined && db.isVisible !== null) ? db.isVisible : true;
                    html += renderDatabaseRow(db, isVisible, false, false);
                }
            } else {
                html += '<div class="empty-folder">✨ Папка пуста</div>';
            }

            html += `</div></div>`;
        }

        // Базы без папки
        if (databasesWithoutFolder.length > 0) {
            html += `
                <div class="folder-item warning-folder">
                    <div class="folder-header" onclick="toggleFolder('no-folder')">
                        <span class="folder-name">⚠️ Базы данных без папки (${databasesWithoutFolder.length})</span>
                        <span class="folder-toggle">▼</span>
                    </div>
                    <div class="folder-databases" id="folder-dbs-no-folder" style="display: none;">
            `;
            for (const db of databasesWithoutFolder) {
                const isVisible = (db.isVisible !== undefined && db.isVisible !== null) ? db.isVisible : true;
                html += renderDatabaseRow(db, isVisible, false, true);
            }
            html += `</div></div>`;
        }

        // Битые базы
        if (orphanedDatabases.length > 0) {
            html += `
                <div class="folder-item error-folder">
                    <div class="folder-header" onclick="toggleFolder('orphaned')">
                        <span class="folder-name">❌ Базы с несуществующей папкой (${orphanedDatabases.length})</span>
                        <span class="folder-toggle">▼</span>
                    </div>
                    <div class="folder-databases" id="folder-dbs-orphaned" style="display: none;">
            `;
            for (const db of orphanedDatabases) {
                const isVisible = (db.isVisible !== undefined && db.isVisible !== null) ? db.isVisible : true;
                html += renderDatabaseRow(db, isVisible, true, false);
            }
            html += `</div></div>`;
        }

        html += '</div>';

        if (foldersMap.size === 0 && databasesWithoutFolder.length === 0 && orphanedDatabases.length === 0) {
            container.innerHTML = '<div class="empty-state">📁 Нет папок и баз данных. Создайте папку или загрузите базу.</div>';
        } else {
            container.innerHTML = html;
        }
    } catch (e) {
        console.error('Failed to load folders list:', e);
        container.innerHTML = `<div class="empty-state">❌ Ошибка загрузки: ${e.message}<br><br><button onclick="loadFoldersList()" class="btn btn-secondary">🔄 Повторить</button></div>`;
    }
}

/**
 * Переключает видимость (разворачивает/сворачивает) папки
 * @param {string} safeId - безопасный идентификатор папки
 */
window.toggleFolder = function(safeId) {
    const dbsContainer = document.getElementById(`folder-dbs-${safeId}`);
    if (!dbsContainer) return;

    const isHidden = dbsContainer.style.display === 'none' || dbsContainer.style.display === '';
    dbsContainer.style.display = isHidden ? 'flex' : 'none';

    const folderItem = dbsContainer.closest('.folder-item');
    const toggle = folderItem ? folderItem.querySelector('.folder-toggle') : null;
    if (toggle) toggle.textContent = isHidden ? '▲' : '▼';
};

// ============================================
// БЫСТРОЕ СОЗДАНИЕ ПАПКИ В ФОРМЕ ЗАГРУЗКИ БД
// ============================================

const quickCreateFolderBtn = document.getElementById('quickCreateFolderBtn');
const quickFolderForm = document.getElementById('quickFolderForm');
const quickFolderName = document.getElementById('quickFolderName');
const quickCreateConfirmBtn = document.getElementById('quickCreateConfirmBtn');
const quickCreateCancelBtn = document.getElementById('quickCreateCancelBtn');

if (quickCreateFolderBtn) {
    quickCreateFolderBtn.addEventListener('click', () => {
        if (quickFolderForm) quickFolderForm.style.display = 'block';
        if (quickFolderName) quickFolderName.focus();
    });
}

if (quickCreateCancelBtn) {
    quickCreateCancelBtn.addEventListener('click', () => {
        if (quickFolderForm) quickFolderForm.style.display = 'none';
        if (quickFolderName) quickFolderName.value = '';
    });
}

if (quickCreateConfirmBtn) {
    quickCreateConfirmBtn.addEventListener('click', async () => {
        const folderName = quickFolderName ? quickFolderName.value.trim() : '';
        if (!folderName) {
            alert('Введите название папки');
            return;
        }

        const params = new URLSearchParams();
        params.append('action', 'createFolder');
        params.append('folderName', folderName);
        addCsrfToParams(params);

        try {
            const response = await fetch('/api/teacher', { method: 'POST', body: params });
            const data = await response.json();
            if (data.success) {
                if (quickFolderForm) quickFolderForm.style.display = 'none';
                if (quickFolderName) quickFolderName.value = '';
                await Promise.all([loadFoldersForSelect(), loadFoldersList()]);
                alert(`Папка "${folderName}" создана`);
            } else {
                alert('Ошибка: ' + data.error);
            }
        } catch (e) {
            alert('Ошибка соединения');
        }
    });
}

// ============================================
// УПРАВЛЕНИЕ БАЗАМИ ДАННЫХ
// ============================================

/**
 * Загружает список баз для выпадающего списка загрузки схемы (только нормальные базы)
 */
async function loadDatabasesForSchemaSelect() {
    const select = document.getElementById('schemaDbSelect');
    if (!select) return;

    try {
        const response = await fetch('/api/teacher?action=listNormalDatabases');
        const data = await response.json();
        select.innerHTML = '<option value="">Выберите базу</option>';

        if (data.databases) {
            for (const db of data.databases) {
                const option = document.createElement('option');
                option.value = db.dbName;
                option.textContent = `${db.displayName} (${db.dbName})`;
                option.dataset.schemaUrl = db.schemaImageUrl || '';
                select.appendChild(option);
            }
        }

        if (select.options.length === 1) {
            select.innerHTML = '<option value="">Нет доступных баз данных</option>';
        }
    } catch (e) {
        console.error('Failed to load databases for schema select:', e);
        select.innerHTML = '<option value="">Ошибка загрузки</option>';
    }
}

/**
 * Загружает список баз для выпадающего списка генерации вопросов Moodle (только нормальные базы)
 */
async function loadDatabasesForMoodleSelect() {
    const select = document.getElementById('moodleDbSelect');
    if (!select) return;

    try {
        const response = await fetch('/api/teacher?action=listNormalDatabases');
        const data = await response.json();
        select.innerHTML = '<option value="">Выберите базу</option>';

        if (data.databases) {
            for (const db of data.databases) {
                const option = document.createElement('option');
                option.value = db.dbName;
                option.textContent = `${db.displayName} (${db.dbName})`;
                select.appendChild(option);
            }
        }

        if (select.options.length === 1) {
            select.innerHTML = '<option value="">Нет доступных баз данных</option>';
        }
    } catch (e) {
        console.error('Failed to load databases for moodle select:', e);
        select.innerHTML = '<option value="">Ошибка загрузки</option>';
    }
}

/**
 * Удаляет базу данных
 * @param {string} dbName - имя базы данных
 */
window.deleteDatabase = async function(dbName) {
    if (!confirm(`Удалить базу данных "${dbName}"? Это действие необратимо.`)) return;

    const params = new URLSearchParams();
    params.append('action', 'delete');
    params.append('dbName', dbName);
    addCsrfToParams(params);

    try {
        const response = await fetch('/api/teacher', { method: 'POST', body: params });
        const data = await response.json();
        if (data.success) {
            alert('База данных удалена');
            await Promise.all([
                loadFoldersList(),
                loadFoldersForSelect(),
                loadDatabasesForSchemaSelect(),
                loadDatabasesForMoodleSelect()
            ]);
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

/**
 * Открывает модальное окно редактирования метаданных базы данных
 */
window.editDatabase = function(dbName, displayName, folderId, isVisible, accessStart, accessEnd, maxRows) {
    const editDbName = document.getElementById('editDbName');
    const editDisplayName = document.getElementById('editDisplayName');
    const editAccessPassword = document.getElementById('editAccessPassword');
    const editRemovePasswordCheckbox = document.getElementById('editRemovePasswordCheckbox');
    const editMaxRows = document.getElementById('editMaxRows');
    const editIsVisible = document.getElementById('editIsVisible');
    const editAccessStart = document.getElementById('editAccessStart');
    const editAccessEnd = document.getElementById('editAccessEnd');
    const editCsrfField = document.getElementById('editCsrfToken');

    if (editDbName) editDbName.value = dbName;
    if (editDisplayName) editDisplayName.value = displayName || '';
    if (editAccessPassword) editAccessPassword.value = '';
    if (editRemovePasswordCheckbox) editRemovePasswordCheckbox.checked = false;
    if (editMaxRows) editMaxRows.value = maxRows || 20;
    if (editIsVisible) editIsVisible.value = (isVisible !== undefined && isVisible !== null) ? (isVisible ? 'true' : 'false') : 'true';
    if (editAccessStart) editAccessStart.value = accessStart || '';
    if (editAccessEnd) editAccessEnd.value = accessEnd || '';
    if (editCsrfField && csrfToken) editCsrfField.value = csrfToken;

    fetch('/api/teacher?action=listFolders')
        .then(response => response.json())
        .then(data => {
            const select = document.getElementById('editFolderId');
            if (!select) return;
            select.innerHTML = '';

            if (data.folders) {
                for (const folder of data.folders) {
                    const option = document.createElement('option');
                    option.value = folder.id;
                    option.textContent = folder.name;
                    if (folder.id == folderId) option.selected = true;
                    select.appendChild(option);
                }
            }

            const modal = document.getElementById('editDatabaseModal');
            if (modal) modal.style.display = 'flex';
        })
        .catch(error => {
            console.error('Failed to load folders:', error);
            alert('Ошибка загрузки списка папок');
        });
};

/**
 * Закрывает модальное окно редактирования базы данных
 */
window.closeEditModal = function() {
    const modal = document.getElementById('editDatabaseModal');
    if (modal) modal.style.display = 'none';
};

/**
 * Сохраняет изменения метаданных базы данных
 */
window.saveDatabaseMetadata = async function() {
    const editDbName = document.getElementById('editDbName');
    const editDisplayName = document.getElementById('editDisplayName');
    const editFolderId = document.getElementById('editFolderId');
    const editAccessPassword = document.getElementById('editAccessPassword');
    const editRemovePasswordCheckbox = document.getElementById('editRemovePasswordCheckbox');
    const editIsVisible = document.getElementById('editIsVisible');
    const editAccessStart = document.getElementById('editAccessStart');
    const editAccessEnd = document.getElementById('editAccessEnd');
    const editMaxRows = document.getElementById('editMaxRows');

    if (!editDbName || !editFolderId) return;

    const formData = new URLSearchParams();
    formData.append('action', 'updateDatabaseMetadata');
    formData.append('dbName', editDbName.value);

    if (editDisplayName && editDisplayName.value) formData.append('displayName', editDisplayName.value);

    const folderId = editFolderId.value;
    if (folderId) {
        formData.append('folderId', folderId);
    } else {
        alert('Выберите папку');
        return;
    }

    addCsrfToParams(formData);

    if (editRemovePasswordCheckbox && editRemovePasswordCheckbox.checked) {
        formData.append('removePassword', 'true');
    } else if (editAccessPassword && editAccessPassword.value) {
        formData.append('accessPassword', editAccessPassword.value);
    }

    if (editIsVisible) formData.append('isVisible', editIsVisible.value);
    if (editAccessStart && editAccessStart.value) formData.append('accessStart', editAccessStart.value);
    if (editAccessEnd && editAccessEnd.value) formData.append('accessEnd', editAccessEnd.value);
    if (editMaxRows && editMaxRows.value) formData.append('maxRows', editMaxRows.value);

    try {
        const response = await fetch('/api/teacher', { method: 'POST', body: formData });
        const data = await response.json();
        if (data.success) {
            alert('Метаданные обновлены');
            window.closeEditModal();
            await Promise.all([
                loadFoldersList(),
                loadFoldersForSelect(),
                loadDatabasesForSchemaSelect(),
                loadDatabasesForMoodleSelect()
            ]);
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения');
    }
};

// ============================================
// ЗАГРУЗКА СХЕМЫ
// ============================================

const schemaDbSelect = document.getElementById('schemaDbSelect');
if (schemaDbSelect) {
    schemaDbSelect.addEventListener('change', function() {
        const selectedOption = this.options[this.selectedIndex];
        const schemaUrl = selectedOption ? selectedOption.dataset.schemaUrl : null;
        const previewContainer = document.getElementById('schemaPreview');
        const previewImg = document.getElementById('schemaPreviewImg');

        if (previewContainer && previewImg && schemaUrl && schemaUrl !== 'null' && schemaUrl !== '') {
            previewImg.src = schemaUrl;
            previewContainer.style.display = 'block';
        } else if (previewContainer) {
            previewContainer.style.display = 'none';
        }
    });
}

const uploadSchemaForm = document.getElementById('uploadSchemaForm');
if (uploadSchemaForm) {
    uploadSchemaForm.addEventListener('submit', async function(e) {
        e.preventDefault();

        const dbName = document.getElementById('schemaDbSelect') ? document.getElementById('schemaDbSelect').value : '';
        const fileInput = document.getElementById('schemaImage');
        const file = fileInput ? fileInput.files[0] : null;

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
        addCsrfToFormData(formData);

        try {
            const response = await fetch('/api/teacher', { method: 'POST', body: formData });
            const data = await response.json();
            if (data.success) {
                alert('Схема успешно загружена');
                await Promise.all([loadFoldersList(), loadDatabasesForSchemaSelect()]);
                if (fileInput) fileInput.value = '';
                updateSchemaFileLabel(fileInput);
            } else {
                alert('Ошибка: ' + data.error);
            }
        } catch (e) {
            alert('Ошибка соединения');
        }
    });
}

// ============================================
// ГЕНЕРАЦИЯ ВОПРОСОВ ДЛЯ MOODLE
// ============================================

/**
 * Обновляет метку выбранного файла с вопросами для Moodle
 * @param {HTMLInputElement} input - элемент input file
 */
function updateMoodleFileLabel(input) {
    const span = document.getElementById('moodleFileName');
    if (span) {
        span.textContent = (input.files && input.files.length > 0) ? `📄 ${input.files[0].name}` : 'Выберите файл (.txt)';
    }
}

const moodleForm = document.getElementById('moodleForm');
if (moodleForm) {
    moodleForm.addEventListener('submit', async function(e) {
        e.preventDefault();

        const dbName = document.getElementById('moodleDbSelect') ? document.getElementById('moodleDbSelect').value : '';
        const category = document.getElementById('moodleCategory') ? document.getElementById('moodleCategory').value : '';
        const formatSelect = document.getElementById('moodleFormat');
        const format = formatSelect ? formatSelect.value : 'gift';
        const fileInput = document.getElementById('questionsFile');
        const file = fileInput ? fileInput.files[0] : null;

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
        addCsrfToFormData(formData);

        const resultDiv = document.getElementById('moodleResult');
        if (resultDiv) {
            resultDiv.style.display = 'block';
            resultDiv.innerHTML = '<div class="empty-state">⏳ Генерация...</div>';
        }

        try {
            const response = await fetch('/api/teacher/moodle', { method: 'POST', body: formData });
            if (response.ok) {
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                const ext = format === 'gift' ? '.gift' : (format === 'xml' ? '.xml' : '.txt');
                a.download = `moodle_questions_${Date.now()}${ext}`;
                a.href = url;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);

                if (resultDiv) {
                    resultDiv.innerHTML = '<div class="empty-state" style="color: green;">✅ Файл сгенерирован и скачан</div>';
                    setTimeout(() => resultDiv.style.display = 'none', 3000);
                }
            } else {
                const error = await response.json();
                if (resultDiv) {
                    resultDiv.innerHTML = `<div class="empty-state" style="color: red;">❌ Ошибка: ${error.error || 'Неизвестная ошибка'}</div>`;
                }
            }
        } catch (err) {
            if (resultDiv) {
                resultDiv.innerHTML = `<div class="empty-state" style="color: red;">❌ Ошибка: ${err.message}</div>`;
            }
        }
    });
}

// ============================================
// РАСШИРЕННОЕ УПРАВЛЕНИЕ ПАПКАМИ
// ============================================

/**
 * Открывает диалог редактирования названия папки
 * @param {number} folderId - идентификатор папки
 * @param {string} currentName - текущее название папки
 */
window.editFolder = function(folderId, currentName) {
    const newName = prompt('Введите новое название папки:', currentName);
    if (newName && newName.trim() && newName !== currentName) {
        updateFolderName(folderId, newName.trim());
    } else if (newName === '') {
        alert('Название папки не может быть пустым');
    }
};

/**
 * Отправляет запрос на обновление названия папки
 * @param {number} folderId - идентификатор папки
 * @param {string} newName - новое название папки
 */
async function updateFolderName(folderId, newName) {
    const params = new URLSearchParams();
    params.append('action', 'updateFolder');
    params.append('folderId', folderId);
    params.append('folderName', newName);
    addCsrfToParams(params);

    try {
        const response = await fetch('/api/teacher', { method: 'POST', body: params });
        const data = await response.json();
        if (data.success) {
            alert('Название папки изменено');
            await Promise.all([
                loadFoldersList(),
                loadFoldersForSelect(),
                loadDatabasesForSchemaSelect(),
                loadDatabasesForMoodleSelect()
            ]);
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения: ' + e.message);
    }
}

/**
 * Удаляет папку (только если она пуста)
 * @param {number} folderId - идентификатор папки
 * @param {string} folderName - название папки
 * @param {number} dbCount - количество баз данных в папке
 */
window.deleteFolder = async function(folderId, folderName, dbCount) {
    if (dbCount > 0) {
        alert(`Невозможно удалить папку "${folderName}": в ней находится ${dbCount} баз(ы) данных. Сначала переместите или удалите базы данных.`);
        return;
    }
    if (!confirm(`Удалить папку "${folderName}"? Папка пуста, удаление безопасно.`)) return;

    const params = new URLSearchParams();
    params.append('action', 'deleteFolder');
    params.append('folderId', folderId);
    addCsrfToParams(params);

    try {
        const response = await fetch('/api/teacher', { method: 'POST', body: params });
        const data = await response.json();
        if (data.success) {
            alert('Папка удалена');
            await Promise.all([
                loadFoldersList(),
                loadFoldersForSelect(),
                loadDatabasesForSchemaSelect(),
                loadDatabasesForMoodleSelect()
            ]);
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения: ' + e.message);
    }
};

/**
 * Открывает диалог перемещения базы данных в другую папку
 * @param {string} dbName - имя базы данных
 * @param {number} currentFolderId - текущий идентификатор папки
 */
window.moveDatabaseToFolder = async function(dbName, currentFolderId) {
    try {
        const response = await fetch('/api/teacher?action=listFolders');
        const data = await response.json();

        if (!data.folders || data.folders.length === 0) {
            alert('Нет доступных папок для перемещения. Сначала создайте папку.');
            return;
        }

        const availableFolders = data.folders.filter(f => f.id != currentFolderId);
        if (availableFolders.length === 0) {
            alert('Нет других папок для перемещения');
            return;
        }

        let message = 'Выберите папку для перемещения базы данных:\n\n';
        availableFolders.forEach((f, i) => {
            message += `${i + 1}. ${f.name} (ID: ${f.id})\n`;
        });
        message += '\nВведите номер или ID папки:';

        const userInput = prompt(message);
        if (!userInput) return;

        let targetFolderId = null;
        const inputNum = parseInt(userInput);

        if (!isNaN(inputNum) && inputNum >= 1 && inputNum <= availableFolders.length) {
            targetFolderId = availableFolders[inputNum - 1].id;
        } else {
            const folder = availableFolders.find(f => f.id == userInput);
            if (folder) {
                targetFolderId = folder.id;
            } else {
                alert('Неверный выбор. Операция отменена.');
                return;
            }
        }

        await executeMoveDatabase(dbName, targetFolderId);
    } catch (e) {
        alert('Ошибка загрузки списка папок: ' + e.message);
    }
};

/**
 * Выполняет перемещение базы данных в указанную папку
 * @param {string} dbName - имя базы данных
 * @param {number} targetFolderId - идентификатор целевой папки
 */
async function executeMoveDatabase(dbName, targetFolderId) {
    const params = new URLSearchParams();
    params.append('action', 'moveDatabaseToFolder');
    params.append('dbName', dbName);
    params.append('targetFolderId', targetFolderId);
    addCsrfToParams(params);

    try {
        const response = await fetch('/api/teacher', { method: 'POST', body: params });
        const data = await response.json();
        if (data.success) {
            alert('База данных перемещена');
            await Promise.all([
                loadFoldersList(),
                loadFoldersForSelect(),
                loadDatabasesForSchemaSelect(),
                loadDatabasesForMoodleSelect()
            ]);
        } else {
            alert('Ошибка: ' + data.error);
        }
    } catch (e) {
        alert('Ошибка соединения: ' + e.message);
    }
}

// ============================================
// ЗАГРУЗКА SQL-СКРИПТА
// ============================================

const uploadForm = document.getElementById('uploadForm');
if (uploadForm) {
    uploadForm.addEventListener('submit', function(e) {
        e.preventDefault();

        const dbName = document.getElementById('dbName') ? document.getElementById('dbName').value.trim() : '';
        const folderId = document.getElementById('folderSelect') ? document.getElementById('folderSelect').value : '';
        const displayName = document.getElementById('displayName') ? document.getElementById('displayName').value.trim() : '';
        const accessPassword = document.getElementById('accessPassword') ? document.getElementById('accessPassword').value : '';
        const fileInput = document.getElementById('sqlFile');
        const file = fileInput ? fileInput.files[0] : null;
        const maxRowsInput = document.getElementById('maxRows');
        const maxRows = maxRowsInput ? maxRowsInput.value : '20';

        if (!dbName) {
            alert('Введите название базы данных');
            return;
        }
        if (!folderId) {
            alert('Выберите папку для базы данных');
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

        if (overlay) overlay.style.display = 'flex';
        if (progressFill) progressFill.style.width = '10%';
        if (loadingStatus) loadingStatus.textContent = 'Отправка файла...';
        if (logMessages) logMessages.innerHTML = '';
        addLogMessage('Начало загрузки файла: ' + file.name, 'info');

        const formData = new FormData();
        formData.append('action', 'upload');
        formData.append('dbName', dbName);
        formData.append('sqlFile', file);
        if (displayName) formData.append('displayName', displayName);
        if (accessPassword) formData.append('accessPassword', accessPassword);
        if (folderId) formData.append('folderId', folderId);
        if (maxRows) formData.append('maxRows', maxRows);
        addCsrfToFormData(formData);

        const eventSource = new EventSource('/api/logs');

        eventSource.onmessage = function(event) {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'start') {
                    addLogMessage('Начало выполнения скрипта. Всего запросов: ' + data.total, 'info');
                } else if (data.type === 'progress') {
                    const percent = Math.round((data.current / data.total) * 100);
                    if (progressFill) progressFill.style.width = percent + '%';
                    if (loadingStatus) loadingStatus.textContent = `Выполнение: ${percent}% (${data.current}/${data.total})`;
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
            if (e.lengthComputable && progressFill) {
                progressFill.style.width = (e.loaded / e.total) * 100 + '%';
            }
        });

        xhr.addEventListener('load', function() {
            if (xhr.status === 200) {
                try {
                    const data = JSON.parse(xhr.responseText);
                    if (data.success) {
                        if (progressFill) progressFill.style.width = '100%';
                        if (loadingStatus) loadingStatus.textContent = 'База данных успешно создана';
                        addLogMessage('✅ База данных успешно создана!', 'success');
                        setTimeout(() => {
                            if (overlay) overlay.style.display = 'none';
                            if (uploadForm) uploadForm.reset();
                            updateFileLabel(document.getElementById('sqlFile'));
                            Promise.all([
                                loadFoldersList(),
                                loadFoldersForSelect(),
                                loadDatabasesForSchemaSelect(),
                                loadDatabasesForMoodleSelect()
                            ]);
                        }, 2000);
                    } else {
                        if (overlay) overlay.style.display = 'none';
                        addLogMessage('❌ Ошибка: ' + data.error, 'error');
                        alert('Ошибка: ' + data.error);
                    }
                } catch (e) {
                    if (overlay) overlay.style.display = 'none';
                    alert('Ошибка при обработке ответа сервера');
                }
            } else {
                if (overlay) overlay.style.display = 'none';
                alert('Ошибка соединения: ' + xhr.status);
            }
        });

        xhr.addEventListener('error', function() {
            if (overlay) overlay.style.display = 'none';
            alert('Ошибка соединения');
        });

        xhr.open('POST', '/api/teacher', true);
        xhr.send(formData);
    });
}

// ============================================
// ИНИЦИАЛИЗАЦИЯ
// ============================================

/**
 * Инициализация страницы: загрузка CSRF-токена и всех списков
 */
(async function init() {
    await loadCsrfToken();
    await Promise.all([
        loadFoldersForSelect(),
        loadFoldersList(),
        loadDatabasesForSchemaSelect(),
        loadDatabasesForMoodleSelect()
    ]);
})();