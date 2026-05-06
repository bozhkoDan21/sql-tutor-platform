<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Trainer - Вход</title>
    <link rel="stylesheet" href="/css/style.css">
    <style>
        .login-container {
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        .login-card {
            background: white;
            padding: 2rem;
            border-radius: 1rem;
            box-shadow: 0 20px 40px rgba(0,0,0,0.2);
            width: 100%;
            max-width: 400px;
        }
        .login-card h1 {
            text-align: center;
            margin-bottom: 0.5rem;
            color: #333;
        }
        .login-card p {
            text-align: center;
            margin-bottom: 1.5rem;
            color: #666;
        }
        .form-group {
            margin-bottom: 1rem;
        }
        .form-group input {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #ddd;
            border-radius: 0.5rem;
            font-size: 1rem;
            box-sizing: border-box;
        }
        .form-group input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }
        .btn-login {
            width: 100%;
            padding: 0.75rem;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 0.5rem;
            font-size: 1rem;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn-login:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
        }
        .error-message {
            color: #ef4444;
            text-align: center;
            margin-top: 1rem;
            display: none;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-card">
            <h1>🔐 SQL Trainer</h1>
            <p>Вход в панель преподавателя</p>
            <form id="loginForm">
                <div class="form-group">
                    <input type="password" id="password" name="password" placeholder="Введите пароль" required autofocus>
                </div>
                <button type="submit" class="btn-login">Войти</button>
                <div id="errorMessage" class="error-message"></div>
            </form>
        </div>
    </div>

    <script>
        document.getElementById('loginForm').addEventListener('submit', async (e) => {
            e.preventDefault();

            const password = document.getElementById('password').value;
            const errorDiv = document.getElementById('errorMessage');

            errorDiv.style.display = 'none';

            try {
                const response = await fetch('/api/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ password: password })
                });

                const data = await response.json();

                if (data.success) {
                    window.location.href = '/teacher';
                } else {
                    errorDiv.textContent = data.error || 'Неверный пароль';
                    errorDiv.style.display = 'block';
                }
            } catch (error) {
                errorDiv.textContent = 'Ошибка соединения с сервером';
                errorDiv.style.display = 'block';
            }
        });
    </script>
</body>
</html>