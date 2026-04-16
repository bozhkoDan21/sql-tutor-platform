package com.sqltrainer.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebServlet("*.css")
public class StaticResourceServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String path = req.getServletPath();

        // Устанавливаем правильный Content-Type
        if (path.endsWith(".css")) {
            resp.setContentType("text/css");
        } else if (path.endsWith(".js")) {
            resp.setContentType("application/javascript");
        } else {
            resp.setContentType("text/plain");
        }
        resp.setCharacterEncoding("UTF-8");

        // Пробуем найти файл
        InputStream is = null;

        // Способ 1: через ServletContext
        is = getServletContext().getResourceAsStream(path);

        // Способ 2: через реальный путь
        if (is == null) {
            try {
                String realPath = getServletContext().getRealPath(path);
                if (realPath != null) {
                    Path filePath = Paths.get(realPath);
                    if (Files.exists(filePath)) {
                        Files.copy(filePath, resp.getOutputStream());
                        return;
                    }
                }
            } catch (Exception e) {
                // Игнорируем
            }
        }

        if (is == null) {
            resp.sendError(404, "File not found: " + path);
            return;
        }

        // Отдаём файл
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            resp.getOutputStream().write(buffer, 0, bytesRead);
        }
        is.close();
    }
}