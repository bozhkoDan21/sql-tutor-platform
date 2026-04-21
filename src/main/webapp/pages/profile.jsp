<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <title>SQL Trainer - Личный профиль</title>
    <link rel="stylesheet" href="/css/style.css?v=2">
    <style>
        .profile-container {
            max-width: 800px;
            margin: 0 auto;
        }
        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1rem;
        }
        .form-group-full {
            margin-bottom: 1rem;
        }
        .password-section {
            margin-top: 1.5rem;
            padding-top: 1.5rem;
            border-top: 1px solid var(--border);
        }
        .password-section h4 {
            margin-bottom: 1rem;
            color: var(--dark);
        }
        .password-requirements {
            font-size: 0.8rem;
            color: var(--text-light);
            margin-top: 0.5rem;
        }
        .password-requirements ul {
            margin: 0.5rem 0 0 1.5rem;
        }
        .btn-save {
            margin-top: 1.5rem;
            width: 100%;
        }
        .info-message {
            background: var(--primary-light);
            color: var(--primary);
            padding: 0.75rem;
            border-radius: var(--radius);
            margin-bottom: 1rem;
            text-align: center;
        }
        .readonly-field {
            background: var(--light);
            color: var(--text-light);
            cursor: not-allowed;
        }
        .name-fields {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: 1rem;
            margin-bottom: 1rem;
        }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-left">
                <h1 class="logo">SQL Trainer</h1>
                <span class="badge">Личный профиль</span>
            </div>
            <div class="nav-right">
                <a href="index" class="nav-link">Тренажёр</a>
                <a href="teacher" id="teacherLink" class="nav-link" style="display: none;">Панель преподавателя</a>
                <a href="profile" class="nav-link active">Профиль</a>
                <a href="#" id="logoutBtn" class="nav-link" style="background: rgba(255,255,255,0.2);">Выйти</a>
            </div>
        </div>
    </nav>

    <main class="container profile-container">
        <div class="page-header">
            <h2 class="page-title">👤 Личный профиль</h2>
            <p class="page-description">Управление личной информацией</p>
        </div>

        <div class="card">
            <div id="infoMessage" class="info-message" style="display: none;"></div>

            <form id="profileForm">
                <div class="form-row">
                    <div class="form-group">
                        <label for="login">Логин</label>
                        <input type="text" id="login" class="form-input readonly-field" readonly disabled>
                    </div>
                    <div class="form-group">
                        <label for="role">Роль</label>
                        <input type="text" id="role" class="form-input readonly-field" readonly disabled>
                    </div>
                </div>

                <div class="name-fields">
                    <div class="form-group">
                        <label for="lastName">Фамилия</label>
                        <input type="text" id="lastName" class="form-input" placeholder="Иванов">
                    </div>
                    <div class="form-group">
                        <label for="firstName">Имя</label>
                        <input type="text" id="firstName" class="form-input" placeholder="Иван">
                    </div>
                    <div class="form-group">
                        <label for="patronymic">Отчество</label>
                        <input type="text" id="patronymic" class="form-input" placeholder="Иванович">
                    </div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label for="email">Email</label>
                        <input type="email" id="email" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="groupName">Группа</label>
                        <input type="text" id="groupName" class="form-input readonly-field" readonly disabled>
                    </div>
                </div>

                <div class="password-section">
                    <h4>🔐 Смена пароля</h4>
                    <div class="form-group">
                        <label for="currentPassword">Текущий пароль</label>
                        <input type="password" id="currentPassword" class="form-input" placeholder="Введите текущий пароль">
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label for="newPassword">Новый пароль</label>
                            <input type="password" id="newPassword" class="form-input" placeholder="Введите новый пароль">
                        </div>
                        <div class="form-group">
                            <label for="confirmPassword">Подтверждение пароля</label>
                            <input type="password" id="confirmPassword" class="form-input" placeholder="Подтвердите новый пароль">
                        </div>
                    </div>
                    <div class="password-requirements">
                        <strong>Требования к паролю:</strong>
                        <ul>
                            <li>Минимум 8 символов</li>
                            <li>Хотя бы одна заглавная буква (A-Z)</li>
                            <li>Хотя бы одна строчная буква (a-z)</li>
                            <li>Хотя бы одна цифра (0-9)</li>
                            <li>Хотя бы один специальный символ (!@#$%^&*)</li>
                        </ul>
                    </div>
                </div>

                <button type="submit" class="btn btn-primary btn-save">💾 Сохранить изменения</button>
            </form>
        </div>
    </main>

    <script>
        let currentUser = null;

        function getAccessToken() {
            return localStorage.getItem('accessToken');
        }

        function getAuthHeader() {
            const token = getAccessToken();
            return token ? { 'Authorization': 'Bearer ' + token } : {};
        }

        // Проверка авторизации
        (function checkAuth() {
            const token = getAccessToken();
            const user = localStorage.getItem('user');
            if (!token || !user) {
                window.location.href = '/login';
                return;
            }
            currentUser = JSON.parse(user);

            // Показываем ссылку на teacher.jsp если роль teacher
            if (currentUser.role === 'teacher') {
                const teacherLink = document.getElementById('teacherLink');
                if (teacherLink) {
                    teacherLink.style.display = 'inline-block';
                }
            }
        })();

        // Загрузка данных профиля
        async function loadProfile() {
            try {
                const response = await fetch('/api/user/profile', {
                    headers: getAuthHeader()
                });
                const data = await response.json();
                if (data.success) {
                    document.getElementById('login').value = data.user.login;
                    document.getElementById('role').value = data.user.role === 'teacher' ? 'Преподаватель' : 'Студент';
                    document.getElementById('lastName').value = data.user.lastName || '';
                    document.getElementById('firstName').value = data.user.firstName || '';
                    document.getElementById('patronymic').value = data.user.patronymic || '';
                    document.getElementById('email').value = data.user.email || '';
                    document.getElementById('groupName').value = data.user.groupName || '—';
                }
            } catch (error) {
                console.error('Failed to load profile:', error);
            }
        }

        function showMessage(message, type) {
            const msgDiv = document.getElementById('infoMessage');
            msgDiv.textContent = message;
            msgDiv.style.backgroundColor = type === 'success' ? '#d1fae5' : '#fee2e2';
            msgDiv.style.color = type === 'success' ? '#065f46' : '#991b1b';
            msgDiv.style.display = 'block';
            setTimeout(() => {
                msgDiv.style.display = 'none';
            }, 3000);
        }

        // Валидация пароля
        function validatePassword(password) {
            const minLength = 8;
            const hasUpper = /[A-Z]/.test(password);
            const hasLower = /[a-z]/.test(password);
            const hasDigit = /[0-9]/.test(password);
            const hasSpecial = /[!@#$%^&*]/.test(password);

            if (password.length < minLength) return false;
            if (!hasUpper) return false;
            if (!hasLower) return false;
            if (!hasDigit) return false;
            if (!hasSpecial) return false;
            return true;
        }

        // Сохранение профиля
        document.getElementById('profileForm').addEventListener('submit', async (e) => {
            e.preventDefault();

            const lastName = document.getElementById('lastName').value;
            const firstName = document.getElementById('firstName').value;
            const patronymic = document.getElementById('patronymic').value;
            const email = document.getElementById('email').value;
            const currentPassword = document.getElementById('currentPassword').value;
            const newPassword = document.getElementById('newPassword').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            // Валидация email
            if (!email) {
                alert('Введите email');
                return;
            }

            // Валидация ФИО
            if (!lastName && !firstName && !patronymic) {
                alert('Заполните хотя бы одно поле ФИО');
                return;
            }

            // Валидация пароля если он меняется
            if (newPassword || confirmPassword) {
                if (newPassword !== confirmPassword) {
                    alert('Новый пароль и подтверждение не совпадают');
                    return;
                }
                if (!validatePassword(newPassword)) {
                    alert('Пароль не соответствует требованиям безопасности');
                    return;
                }
            }

            const updateData = {
                lastName: lastName,
                firstName: firstName,
                patronymic: patronymic,
                email: email
            };

            if (newPassword && currentPassword) {
                updateData.currentPassword = currentPassword;
                updateData.newPassword = newPassword;
            }

            try {
                const response = await fetch('/api/user/profile', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + getAccessToken()
                    },
                    body: JSON.stringify(updateData)
                });
                const data = await response.json();
                if (data.success) {
                    showMessage('Профиль успешно обновлён', 'success');
                    document.getElementById('currentPassword').value = '';
                    document.getElementById('newPassword').value = '';
                    document.getElementById('confirmPassword').value = '';

                    // Обновляем данные в localStorage
                    const userResponse = await fetch('/api/user/profile', {
                        headers: getAuthHeader()
                    });
                    const userData = await userResponse.json();
                    if (userData.success) {
                        const storedUser = JSON.parse(localStorage.getItem('user'));
                        storedUser.fullName = (lastName + ' ' + firstName + ' ' + patronymic).trim();
                        localStorage.setItem('user', JSON.stringify(storedUser));
                    }
                } else {
                    alert('Ошибка: ' + data.error);
                }
            } catch (error) {
                console.error('Failed to update profile:', error);
                alert('Ошибка сохранения профиля');
            }
        });

        // Кнопка выхода
        document.getElementById('logoutBtn').addEventListener('click', async (e) => {
            e.preventDefault();
            const token = getAccessToken();
            if (token) {
                try {
                    await fetch('/api/auth/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                } catch(e) {}
            }
            localStorage.clear();
            window.location.href = '/login';
        });

        loadProfile();
    </script>
</body>
</html>