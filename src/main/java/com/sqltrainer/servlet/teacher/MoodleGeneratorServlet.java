package com.sqltrainer.servlet.teacher;

import com.sqltrainer.util.MoodleGenerator;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/teacher/moodle")
@MultipartConfig(maxFileSize = 1024 * 1024, maxRequestSize = 1024 * 1024)
public class MoodleGeneratorServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MoodleGeneratorServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();

        try {
            String dbName = req.getParameter("database");
            String categoryName = req.getParameter("category");
            String format = req.getParameter("format");
            Part filePart = req.getPart("questionsFile");

            if (dbName == null || dbName.isEmpty()) {
                response.put("success", false);
                response.put("error", "Database name is required");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            if (categoryName == null || categoryName.isEmpty()) {
                categoryName = "SQL Questions from " + dbName;
            }

            if (filePart == null || filePart.getSize() == 0) {
                response.put("success", false);
                response.put("error", "Questions file is required");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            // 1. Парсим файл с вопросами
            List<MoodleGenerator.Question> questions = MoodleGenerator.parseQuestions(filePart.getInputStream());

            if (questions.isEmpty()) {
                response.put("success", false);
                response.put("error", "No valid questions found in file");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            log.info("Parsed {} questions from file", questions.size());

            // 2. Выполняем запросы для получения эталонных результатов
            questions = MoodleGenerator.executeQueries(questions, dbName);

            // 3. Генерируем выбранный формат
            String filename;
            String content;
            String contentType;

            if ("gift".equals(format)) {
                content = MoodleGenerator.generateGiftFormat(questions, categoryName);
                filename = "moodle_questions_" + System.currentTimeMillis() + ".gift";
                contentType = "text/plain";
            } else if ("xml".equals(format)) {
                content = MoodleGenerator.generateMoodleXml(questions, dbName, categoryName);
                filename = "moodle_questions_" + System.currentTimeMillis() + ".xml";
                contentType = "application/xml";
            } else {
                content = MoodleGenerator.generateTextFormat(questions);
                filename = "questions_preview_" + System.currentTimeMillis() + ".txt";
                contentType = "text/plain";
            }

            // 4. Отправляем файл на скачивание
            resp.setContentType(contentType);
            resp.setHeader("Content-Disposition", "attachment; filename=" + filename);
            resp.getWriter().write(content);

            log.info("Generated {} format with {} questions for database: {}", format, questions.size(), dbName);

        } catch (Exception e) {
            log.error("Failed to generate Moodle file", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(response));
        }
    }
}