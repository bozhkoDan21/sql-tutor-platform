<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SQL Tutor - Вход для преподавателя</title>
    <link rel="stylesheet" href="style.css">
    <style>
        .login-container {
            max-width: 400px;
            margin: 100px auto;
        }
        .login-header {
            text-align: center;
            margin-bottom: 2rem;
        }
        .login-header h1 {
            color: var(--primary);
            font-size: 2rem;
            margin-bottom: 0.5rem;
        }
        .login-header p {
            color: var(--text-light);
        }
        .error-message {
            background: var(--danger-light);
            color: var(--danger);
            padding: 0.75rem;
            border-radius: var(--radius);
            margin-bottom: 1rem;
            text-align: center;
            display: <%=(request.getParameter("error") != null ? "block" : "none")%>;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="card">
            <div class="login-header">
                <h1>🔐 SQL Tutor</h1>
                <p>Вход в панель преподавателя</p>
            </div>

            <div class="error-message" id="errorMsg">
                ❌ Неверный пароль
            </div>

            <form method="post" action="teacher-login" class="upload-form">
                <div class="form-group">
                    <label class="form-label">Пароль доступа</label>
                    <input type="password" name="password" class="form-input"
                           placeholder="Введите пароль" required autofocus>
                </div>

                <button type="submit" class="btn btn-primary" style="width: 100%;">
                    Войти в панель
                </button>
            </form>

            <div style="text-align: center; margin-top: 1.5rem;">
                <a href="index.jsp" style="color: var(--primary); text-decoration: none;">
                    ← Вернуться на страницу студента
                </a>
            </div>
        </div>

        <div style="text-align: center; margin-top: 1rem; color: var(--text-light); font-size: 0.875rem;">
            Только для преподавателей
        </div>
    </div>
</body>
</html>