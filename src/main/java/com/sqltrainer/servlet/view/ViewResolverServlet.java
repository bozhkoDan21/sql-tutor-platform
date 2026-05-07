package com.sqltrainer.servlet.view;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Сервлет для разрешения путей к JSP страницам.
 * Позволяет использовать чистые URL без расширения .jsp.
 * Страницы физически расположены в папке /pages.
 */
@WebServlet({"/index", "/teacher", "/login"})
public class ViewResolverServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();
        String jspPage;

        // Маппинг URL -> JSP файл в папке pages
        switch (path) {
            case "/index":
                jspPage = "/pages/index.jsp";
                break;
            case "/teacher":
                // Проверяем аутентификацию перед показом панели преподавателя
                HttpSession session = req.getSession(false);
                if (session == null || session.getAttribute("authenticated") == null) {
                    resp.sendRedirect("/login");
                    return;
                }
                jspPage = "/pages/teacher.jsp";
                break;
            case "/login":
                jspPage = "/pages/login.jsp";
                break;
            default:
                jspPage = "/pages/index.jsp";
        }

        try {
            req.getRequestDispatcher(jspPage).forward(req, resp);
        } catch (Exception e) {
            resp.sendError(404, "Страница не найдена: " + jspPage);
        }
    }
}