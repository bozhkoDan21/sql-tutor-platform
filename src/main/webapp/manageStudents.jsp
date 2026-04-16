<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <title>SQL Trainer - Управление студентами</title>
    <link rel="stylesheet" href="style.css?v=2">
    <style>
        .search-section {
            display: flex;
            gap: 1rem;
            margin-bottom: 1.5rem;
            flex-wrap: wrap;
        }
        .search-section input {
            flex: 1;
            padding: 0.75rem 1rem;
            border: 2px solid var(--border);
            border-radius: var(--radius);
            font-size: 1rem;
        }
        .search-section select {
            padding: 0.75rem 1rem;
            border: 2px solid var(--border);
            border-radius: var(--radius);
            font-size: 1rem;
            background: var(--white);
            min-width: 150px;
        }
        .students-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.85rem;
        }
        .students-table th {
            background: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
            color: white;
            padding: 0.75rem 1rem;
            text-align: left;
            font-weight: 600;
            position: sticky;
            top: 0;
        }
        .students-table td {
            padding: 0.5rem 1rem;
            border-bottom: 1px solid var(--border);
        }
        .students-table tr:hover td {
            background: var(--primary-light);
        }
        .action-buttons {
            display: flex;
            gap: 0.5rem;
        }
        .btn-icon {
            padding: 0.25rem 0.5rem;
            border-radius: var(--radius-sm);
            cursor: pointer;
            border: none;
            font-size: 1rem;
            background: none;
        }
        .btn-edit {
            color: var(--primary);
        }
        .btn-edit:hover {
            background: var(--primary-light);
        }
        .btn-delete {
            color: var(--danger);
        }
        .btn-delete:hover {
            background: var(--danger-light);
        }
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            justify-content: center;
            align-items: center;
            z-index: 1000;
        }
        .modal-content {
            background: var(--white);
            padding: 2rem;
            border-radius: var(--radius);
            max-width: 500px;
            width: 90%;
        }
        .modal-content h3 {
            margin-bottom: 1rem;
        }
        .modal-buttons {
            display: flex;
            justify-content: flex-end;
            gap: 1rem;
            margin-top: 1.5rem;
        }

        /* ===== НОВЫЕ СТИЛИ ДЛЯ ПАГИНАЦИИ ===== */
        .pagination {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 0.5rem;
            margin-top: 1.5rem;
            flex-wrap: wrap;
            padding: 0.5rem;
        }
        .page-btn {
            padding: 0.5rem 0.75rem;
            min-width: 2.5rem;
            border: 1px solid var(--border);
            background: var(--white);
            border-radius: var(--radius-sm);
            cursor: pointer;
            font-size: 0.875rem;
            transition: all 0.2s;
        }
        .page-btn:hover {
            background: var(--primary-light);
            border-color: var(--primary);
        }
        .page-btn.active {
            background: var(--primary);
            color: white;
            border-color: var(--primary);
        }
        .page-btn.disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .page-btn.disabled:hover {
            background: var(--white);
            border-color: var(--border);
        }
        .pagination-nav {
            background: var(--primary-light);
            font-weight: bold;
        }
        .pagination-dots {
            padding: 0.5rem;
            color: var(--text-light);
        }

        .form-input {
            width: 100%;
            padding: 0.5rem;
            border: 1px solid var(--border);
            border-radius: var(--radius-sm);
            font-size: 0.9rem;
        }
        .form-group {
            margin-bottom: 1rem;
        }
        .form-group label {
            display: block;
            margin-bottom: 0.25rem;
            font-weight: 500;
        }
        .name-row {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: 0.5rem;
        }
    </style>
</head>
<body>
    <!-- ... весь остальной HTML остаётся без изменений ... -->
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Управление студентами</span>
            </div>
            <div class="nav-right">
                <a href="index.jsp" class="nav-link">Тренажёр</a>
                <a href="teacher.jsp" class="nav-link">Панель преподавателя</a>
                <a href="manageStudents.jsp" class="nav-link active">Студенты</a>
                <a href="profile.jsp" class="nav-link">Профиль</a>
                <a href="#" id="logoutBtn" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <main class="container">
        <div class="page-header">
            <h2 class="page-title">👥 Управление студентами</h2>
            <p class="page-description">Просмотр, редактирование и удаление студентов</p>
        </div>

        <div class="card">
            <div class="search-section">
                <input type="text" id="searchInput" placeholder="🔍 Поиск по ФИО, логину или email..." onkeyup="debounceSearch()">
                <select id="groupFilter">
                    <option value="">Все группы</option>
                </select>
            </div>

            <div style="overflow-x: auto;">
                <table class="students-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Логин</th>
                            <th>Фамилия</th>
                            <th>Имя</th>
                            <th>Отчество</th>
                            <th>Email</th>
                            <th>Группа</th>
                            <th>Действия</th>
                        </tr>
                    </thead>
                    <tbody id="studentsTableBody">
                        <tr><td colspan="8" class="empty-state">Загрузка...</td></tr>
                    </tbody>
                </table>
            </div>

            <div class="pagination" id="pagination"></div>
        </div>
    </main>

    <div id="editModal" class="modal">
        <div class="modal-content">
            <h3>✏️ Редактировать студента</h3>
            <input type="hidden" id="editStudentId">
            <div class="name-row">
                <div class="form-group">
                    <label>Фамилия</label>
                    <input type="text" id="editLastName" class="form-input" placeholder="Иванов">
                </div>
                <div class="form-group">
                    <label>Имя</label>
                    <input type="text" id="editFirstName" class="form-input" placeholder="Иван">
                </div>
                <div class="form-group">
                    <label>Отчество</label>
                    <input type="text" id="editPatronymic" class="form-input" placeholder="Иванович">
                </div>
            </div>
            <div class="form-group">
                <label>Email</label>
                <input type="email" id="editEmail" class="form-input">
            </div>
            <div class="form-group">
                <label>Группа</label>
                <input type="text" id="editGroupName" class="form-input">
            </div>
            <div class="modal-buttons">
                <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
                <button class="btn btn-primary" onclick="saveStudent()">Сохранить</button>
            </div>
        </div>
    </div>

    <script>
        let currentStudents = [];
        let currentPage = 1;
        let itemsPerPage = 20;
        let searchTimeout;

        // ============================================
        // РАБОТА С ТОКЕНОМ
        // ============================================

        function getAccessToken() {
            return localStorage.getItem('accessToken');
        }

        function getRefreshToken() {
            return localStorage.getItem('refreshToken');
        }

        async function refreshAccessToken() {
            const refreshToken = getRefreshToken();
            if (!refreshToken) {
                throw new Error('No refresh token');
            }

            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: refreshToken })
            });

            if (!response.ok) {
                throw new Error('Refresh failed');
            }

            const data = await response.json();
            if (data.accessToken) {
                localStorage.setItem('accessToken', data.accessToken);
                return data.accessToken;
            }
            throw new Error('No access token in response');
        }

        async function fetchWithAuth(url, options = {}) {
            let token = getAccessToken();

            if (!token) {
                throw new Error('No token');
            }

            const headers = {
                ...options.headers,
                'Authorization': 'Bearer ' + token
            };

            let response = await fetch(url, { ...options, headers });

            if (response.status === 401) {
                try {
                    const newToken = await refreshAccessToken();
                    headers['Authorization'] = 'Bearer ' + newToken;
                    response = await fetch(url, { ...options, headers });
                } catch (refreshError) {
                    console.error('Refresh failed:', refreshError);
                    localStorage.clear();
                    window.location.href = '/login.jsp';
                    throw new Error('Session expired');
                }
            }

            return response;
        }

        // ============================================
        // ПРОВЕРКА АВТОРИЗАЦИИ
        // ============================================

        (async function checkAuth() {
            const token = getAccessToken();
            const user = localStorage.getItem('user');

            if (!token || !user) {
                window.location.href = '/login.jsp';
                return;
            }

            try {
                const userData = JSON.parse(user);
                if (userData.role !== 'teacher') {
                    window.location.href = '/login.jsp';
                    return;
                }

                const response = await fetch('/api/user/profile', {
                    headers: { 'Authorization': 'Bearer ' + token }
                });

                if (response.status === 401) {
                    try {
                        await refreshAccessToken();
                    } catch (e) {
                        localStorage.clear();
                        window.location.href = '/login.jsp';
                    }
                }
            } catch(e) {
                console.error('Auth check error:', e);
                window.location.href = '/login.jsp';
            }
        })();

        // ============================================
        // ЗАГРУЗКА СПИСКА СТУДЕНТОВ
        // ============================================

        function debounceSearch() {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(loadStudents, 300);
        }

        async function loadStudents() {
            const search = document.getElementById('searchInput').value;
            const group = document.getElementById('groupFilter').value;

            const tbody = document.getElementById('studentsTableBody');
            tbody.innerHTML = '<tr><td colspan="8" class="empty-state">Загрузка...</td></tr>';

            try {
                const url = '/api/teacher/students/list?search=' + encodeURIComponent(search) + '&group=' + encodeURIComponent(group);
                const response = await fetchWithAuth(url, { method: 'GET' });

                const data = await response.json();

                if (data.success) {
                    currentStudents = data.students || [];
                    currentPage = 1;
                    renderStudents();
                    renderPagination();
                    updateGroupFilter(data.groups || []);
                } else {
                    tbody.innerHTML = '<tr><td colspan="8" class="empty-state">Ошибка: ' + (data.error || 'Неизвестная ошибка') + '</td></tr>';
                }
            } catch (error) {
                console.error('Failed to load students:', error);
                tbody.innerHTML = '<tr><td colspan="8" class="empty-state">❌ Ошибка загрузки: ' + error.message + '</td></tr>';
            }
        }

        // ============================================
        // ОТОБРАЖЕНИЕ СПИСКА
        // ============================================

        function parseFullName(fullName) {
            if (!fullName) return { lastName: '', firstName: '', patronymic: '' };
            const parts = fullName.trim().split(' ');
            return {
                lastName: parts[0] || '',
                firstName: parts[1] || '',
                patronymic: parts[2] || ''
            };
        }

        function renderStudents() {
            const tbody = document.getElementById('studentsTableBody');
            const start = (currentPage - 1) * itemsPerPage;
            const end = start + itemsPerPage;
            const pageStudents = currentStudents.slice(start, end);

            if (pageStudents.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="empty-state">Нет студентов</td></tr>';
                return;
            }

            let html = '';
            for (let i = 0; i < pageStudents.length; i++) {
                const student = pageStudents[i];
                const parsedName = parseFullName(student.fullName || '');

                html += '<tr>';
                html += '<td>' + student.id + '</td>';
                html += '<td>' + escapeHtml(student.login) + '</td>';
                html += '<td>' + escapeHtml(parsedName.lastName) + '</td>';
                html += '<td>' + escapeHtml(parsedName.firstName) + '</td>';
                html += '<td>' + escapeHtml(parsedName.patronymic) + '</td>';
                html += '<td>' + escapeHtml(student.email || '') + '</td>';
                html += '<td>' + escapeHtml(student.groupName || '—') + '</td>';
                html += '<td class="action-buttons">';
                html += '<button class="btn-icon btn-edit" onclick="openEditModal(' + student.id + ')" title="Редактировать">✏️</button>';
                html += '<button class="btn-icon btn-delete" onclick="deleteStudent(' + student.id + ')" title="Удалить">🗑️</button>';
                html += '</td>';
                html += '</tr>';
            }
            tbody.innerHTML = html;
        }

        // ===== НОВАЯ ФУНКЦИЯ ПАГИНАЦИИ С УМНЫМИ КНОПКАМИ =====
        function renderPagination() {
            const totalPages = Math.ceil(currentStudents.length / itemsPerPage);
            const pagination = document.getElementById('pagination');

            if (totalPages <= 1) {
                pagination.innerHTML = '';
                return;
            }

            let html = '';

            // Кнопка "В начало"
            if (currentPage > 1) {
                html += '<button class="page-btn pagination-nav" onclick="goToPage(1)" title="Первая страница">⏮</button>';
                html += '<button class="page-btn pagination-nav" onclick="goToPage(' + (currentPage - 1) + ')" title="Предыдущая">◀</button>';
            } else {
                html += '<button class="page-btn disabled" disabled>⏮</button>';
                html += '<button class="page-btn disabled" disabled>◀</button>';
            }

            // Максимум 7 кнопок с номерами страниц
            const maxVisiblePages = 7;
            let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
            let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

            // Корректировка если мы в конце
            if (endPage - startPage + 1 < maxVisiblePages) {
                startPage = Math.max(1, endPage - maxVisiblePages + 1);
            }

            // Первая страница и троеточие
            if (startPage > 1) {
                html += '<button class="page-btn" onclick="goToPage(1)">1</button>';
                if (startPage > 2) {
                    html += '<span class="pagination-dots">...</span>';
                }
            }

            // Номера страниц
            for (let i = startPage; i <= endPage; i++) {
                html += '<button class="page-btn' + (i === currentPage ? ' active' : '') + '" onclick="goToPage(' + i + ')">' + i + '</button>';
            }

            // Последняя страница и троеточие
            if (endPage < totalPages) {
                if (endPage < totalPages - 1) {
                    html += '<span class="pagination-dots">...</span>';
                }
                html += '<button class="page-btn" onclick="goToPage(' + totalPages + ')">' + totalPages + '</button>';
            }

            // Кнопка "В конец"
            if (currentPage < totalPages) {
                html += '<button class="page-btn pagination-nav" onclick="goToPage(' + (currentPage + 1) + ')" title="Следующая">▶</button>';
                html += '<button class="page-btn pagination-nav" onclick="goToPage(' + totalPages + ')" title="Последняя страница">⏭</button>';
            } else {
                html += '<button class="page-btn disabled" disabled>▶</button>';
                html += '<button class="page-btn disabled" disabled>⏭</button>';
            }

            // Добавляем информацию о количестве страниц
            html += '<span style="margin-left: 1rem; font-size: 0.8rem; color: var(--text-light);">';
            html += 'Страница ' + currentPage + ' из ' + totalPages;
            html += ' (всего ' + currentStudents.length + ' студентов)';
            html += '</span>';

            pagination.innerHTML = html;
        }

        function goToPage(page) {
            const totalPages = Math.ceil(currentStudents.length / itemsPerPage);
            if (page < 1 || page > totalPages) return;
            currentPage = page;
            renderStudents();
            renderPagination();
        }

        function updateGroupFilter(groups) {
            const select = document.getElementById('groupFilter');
            const currentValue = select.value;
            select.innerHTML = '<option value="">Все группы</option>';
            if (groups && groups.length > 0) {
                for (const group of groups) {
                    const option = document.createElement('option');
                    option.value = group;
                    option.textContent = group;
                    if (group === currentValue) option.selected = true;
                    select.appendChild(option);
                }
            }
        }

        // ============================================
        // РЕДАКТИРОВАНИЕ СТУДЕНТА
        // ============================================

        function buildFullName(lastName, firstName, patronymic) {
            let parts = [];
            if (lastName && lastName.trim()) parts.push(lastName.trim());
            if (firstName && firstName.trim()) parts.push(firstName.trim());
            if (patronymic && patronymic.trim()) parts.push(patronymic.trim());
            return parts.join(' ');
        }

        function openEditModal(studentId) {
            const student = currentStudents.find(function(s) { return s.id === studentId; });
            if (!student) return;

            const parsedName = parseFullName(student.fullName || '');

            document.getElementById('editStudentId').value = student.id;
            document.getElementById('editLastName').value = parsedName.lastName;
            document.getElementById('editFirstName').value = parsedName.firstName;
            document.getElementById('editPatronymic').value = parsedName.patronymic;
            document.getElementById('editEmail').value = student.email || '';
            document.getElementById('editGroupName').value = student.groupName || '';
            document.getElementById('editModal').style.display = 'flex';
        }

        function closeModal() {
            document.getElementById('editModal').style.display = 'none';
        }

        async function saveStudent() {
            const id = document.getElementById('editStudentId').value;
            const lastName = document.getElementById('editLastName').value;
            const firstName = document.getElementById('editFirstName').value;
            const patronymic = document.getElementById('editPatronymic').value;
            const email = document.getElementById('editEmail').value;
            const groupName = document.getElementById('editGroupName').value;

            const fullName = buildFullName(lastName, firstName, patronymic);

            if (!fullName && !email) {
                alert('Заполните хотя бы одно поле (ФИО или Email)');
                return;
            }

            try {
                const response = await fetchWithAuth('/api/teacher/students/update', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        id: parseInt(id),
                        fullName: fullName,
                        email: email,
                        groupName: groupName
                    })
                });

                const data = await response.json();

                if (data.success) {
                    closeModal();
                    await loadStudents();
                    alert('Студент успешно обновлён');
                } else {
                    alert('Ошибка: ' + (data.error || 'Неизвестная ошибка'));
                }
            } catch (error) {
                console.error('Failed to update student:', error);
                alert('Ошибка сохранения: ' + error.message);
            }
        }

        // ============================================
        // УДАЛЕНИЕ СТУДЕНТА
        // ============================================

        async function deleteStudent(id) {
            if (!confirm('Удалить студента? Это действие необратимо.')) return;

            try {
                const response = await fetchWithAuth('/api/teacher/students/delete?id=' + id, {
                    method: 'DELETE'
                });

                const data = await response.json();

                if (data.success) {
                    await loadStudents();
                    alert('Студент удалён');
                } else {
                    alert('Ошибка: ' + (data.error || 'Неизвестная ошибка'));
                }
            } catch (error) {
                console.error('Failed to delete student:', error);
                alert('Ошибка удаления: ' + error.message);
            }
        }

        // ============================================
        // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
        // ============================================

        function escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // ============================================
        // ОБРАБОТЧИКИ СОБЫТИЙ
        // ============================================

        document.getElementById('groupFilter').addEventListener('change', function() {
            currentPage = 1;
            loadStudents();
        });

        document.getElementById('logoutBtn').addEventListener('click', async function(e) {
            e.preventDefault();
            const token = getAccessToken();
            if (token) {
                try {
                    await fetch('/api/auth/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                } catch(e) {
                    console.error('Logout error:', e);
                }
            }
            localStorage.clear();
            window.location.href = '/login.jsp';
        });

        loadStudents();
    </script>
</body>
</html>