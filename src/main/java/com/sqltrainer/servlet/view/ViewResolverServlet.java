package com.sqltrainer.servlet.view;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Сервлет для разрешения путей к JSP страницам.
 * Позволяет использовать чистые URL без расширения .jsp.
 * Страницы физически расположены в папке /pages.
 */
@WebServlet({"/login", "/index", "/profile", "/teacher", "/manageStudents"})
public class ViewResolverServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();
        String jspPage;

        // Маппинг URL -> JSP файл в папке pages
        switch (path) {
            case "/login":
                jspPage = "/pages/login.jsp";
                break;
            case "/index":
                jspPage = "/pages/index.jsp";
                break;
            case "/profile":
                jspPage = "/pages/profile.jsp";
                break;
            case "/teacher":
                jspPage = "/pages/teacher.jsp";
                break;
            case "/manageStudents":
                jspPage = "/pages/manageStudents.jsp";
                break;
            default:
                jspPage = "/pages/login.jsp";
        }

        try {
            req.getRequestDispatcher(jspPage).forward(req, resp);
        } catch (Exception e) {
            resp.sendError(404, "Page not found: " + jspPage);
        }
    }
}