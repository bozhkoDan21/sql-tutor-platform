package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/teacher-login")
public class TeacherLoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String password = req.getParameter("password");
        String teacherSecret = DatabaseConfig.getTeacherSecret();

        if (teacherSecret != null && teacherSecret.equals(password)) {
            // Создаем сессию для преподавателя
            HttpSession session = req.getSession(true);
            session.setAttribute("role", "teacher");
            session.setMaxInactiveInterval(8 * 60 * 60); // 8 часов

            resp.sendRedirect("teacher.jsp");
        } else {
            resp.sendRedirect("teacher-login.jsp?error=1");
        }
    }
}