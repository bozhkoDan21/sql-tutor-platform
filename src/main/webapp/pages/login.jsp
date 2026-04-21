<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <title>SQL Trainer - Вход</title>
    <link rel="stylesheet" href="/css/style.css?v=2">
    <style>
        .login-container {
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 1rem;
        }
        .login-card {
            max-width: 400px;
            width: 100%;
            background: white;
            border-radius: 1rem;
            box-shadow: 0 20px 40px rgba(0,0,0,0.2);
            overflow: hidden;
        }
        .login-header {
            background: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
            padding: 2rem;
            text-align: center;
            color: white;
        }
        .login-header h1 { font-size: 1.8rem; margin-bottom: 0.5rem; }
        .login-header p { opacity: 0.9; font-size: 0.9rem; }
        .login-body { padding: 2rem; }
        .form-group { margin-bottom: 1.5rem; }
        .form-group label {
            display: block;
            margin-bottom: 0.5rem;
            font-weight: 500;
            color: var(--dark);
        }
        .form-group input {
            width: 100%;
            padding: 0.75rem 1rem;
            border: 2px solid var(--border);
            border-radius: 0.5rem;
            font-size: 1rem;
            transition: all 0.2s;
        }
        .form-group input:focus {
            outline: none;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
        }
        .login-btn {
            width: 100%;
            padding: 0.875rem;
            background: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
            color: white;
            border: none;
            border-radius: 0.5rem;
            font-size: 1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .login-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
        }
        .error-message {
            background: var(--danger-light);
            color: var(--danger);
            padding: 0.75rem;
            border-radius: 0.5rem;
            margin-top: 1rem;
            text-align: center;
            display: none;
        }
        .info-message {
            background: var(--primary-light);
            color: var(--primary);
            padding: 0.75rem;
            border-radius: 0.5rem;
            margin-bottom: 1rem;
            text-align: center;
            font-size: 0.85rem;
        }
        .info-message code {
            background: rgba(0,0,0,0.05);
            padding: 0.2rem 0.4rem;
            border-radius: 0.25rem;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-card">
            <div class="login-header">
                <h1>🔐 SQL Trainer</h1>
                <p>Учебная платформа для практического освоения SQL</p>
            </div>
            <div class="login-body">
                <form id="loginForm">
                    <div class="form-group">
                        <label for="login">Логин</label>
                        <input type="text" id="login" name="login" placeholder="Введите логин" required autocomplete="username">
                    </div>
                    <div class="form-group">
                        <label for="password">Пароль</label>
                        <input type="password" id="password" name="password" placeholder="Введите пароль" required autocomplete="current-password">
                    </div>
                    <button type="submit" class="login-btn">Войти</button>
                </form>
                <div id="errorMessage" class="error-message"></div>
            </div>
        </div>
    </div>

    <script>
        // Обработка формы
        document.getElementById('loginForm').addEventListener('submit', async (e) => {
            e.preventDefault();

            const login = document.getElementById('login').value;
            const password = document.getElementById('password').value;
            const errorDiv = document.getElementById('errorMessage');

            errorDiv.style.display = 'none';
            errorDiv.textContent = '';

            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        login: login,
                        password: password
                    })
                });

                const data = await response.json();

                if (response.ok && data.success) {
                    localStorage.setItem('accessToken', data.accessToken);
                    localStorage.setItem('refreshToken', data.refreshToken);
                    localStorage.setItem('user', JSON.stringify(data.user));

                    console.log('Token saved:', !!data.accessToken);
                    console.log('User role:', data.user.role);

                    if (data.user.role === 'teacher') {
                        window.location.href = '/teacher';
                    } else {
                        window.location.href = '/index';
                    }
                } else {
                    errorDiv.textContent = data.error || 'Ошибка входа. Проверьте логин и пароль.';
                    errorDiv.style.display = 'block';
                }
            } catch (error) {
                console.error('Login error:', error);
                errorDiv.textContent = 'Ошибка соединения с сервером';
                errorDiv.style.display = 'block';
            }
        });

        // Проверяем, не залогинен ли уже пользователь
        const token = localStorage.getItem('accessToken');
        const user = localStorage.getItem('user');
        if (token && user) {
            try {
                const userData = JSON.parse(user);
                if (userData.role === 'teacher') {
                    window.location.href = '/teacher';
                } else {
                    window.location.href = '/index';
                }
            } catch(e) {}
        }
    </script>
</body>
</html>