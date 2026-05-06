package com.sqltrainer.servlet.staticres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@WebServlet("/uploads/schemas/*")
public class ImageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.sendError(404);
            return;
        }

        // Получаем имя файла из URL
        String fileName = pathInfo.substring(1);

        // Путь к файлу внутри Docker контейнера
        String basePath = "/usr/local/tomcat/uploads/schemas/";
        File file = new File(basePath, fileName);

        if (!file.exists()) {
            // Пробуем альтернативный путь
            basePath = getServletContext().getRealPath("/uploads/schemas/");
            file = new File(basePath, fileName);
        }

        if (!file.exists()) {
            resp.sendError(404, "Image not found: " + fileName);
            return;
        }

        // Устанавливаем Content-Type
        String mimeType = getServletContext().getMimeType(file.getName());
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }
        resp.setContentType(mimeType);
        resp.setContentLengthLong(file.length());

        // Отдаём файл
        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = resp.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}