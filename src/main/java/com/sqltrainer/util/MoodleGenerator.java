package com.sqltrainer.util;

import com.sqltrainer.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Генератор вопросов для Moodle в форматах XML и GIFT.
 */
public class MoodleGenerator {
    private static final Logger log = LoggerFactory.getLogger(MoodleGenerator.class);

    /**
     * Задание для Moodle
     */
    public static class Question {
        private final String text;
        private final String query;
        private String expectedResult;

        public Question(String text, String query) {
            this.text = text;
            this.query = query;
        }

        public String getText() { return text; }
        public String getQuery() { return query; }
        public String getExpectedResult() { return expectedResult; }
        public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    }

    /**
     * Парсит входной файл с заданиями
     * Формат: каждая пара строк: текст задания, затем SQL запрос
     */
    public static List<Question> parseQuestions(InputStream inputStream) throws IOException {
        List<Question> questions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            List<String> lines = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }

            if (lines.size() % 2 != 0) {
                throw new IOException("File must contain pairs of lines: question text and SQL query");
            }

            for (int i = 0; i < lines.size(); i += 2) {
                String questionText = lines.get(i);
                String sqlQuery = lines.get(i + 1);

                if (sqlQuery.isEmpty()) {
                    throw new IOException("SQL query cannot be empty for question: " + questionText);
                }

                questions.add(new Question(questionText, sqlQuery));
            }
        }

        return questions;
    }

    /**
     * Выполняет запросы и получает эталонные результаты
     */
    public static List<Question> executeQueries(List<Question> questions, String dbName) {
        for (Question q : questions) {
            try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
                 Statement stmt = conn.createStatement()) {

                stmt.setQueryTimeout(10);

                boolean isSelect = q.getQuery().trim().toLowerCase().startsWith("select");

                if (isSelect) {
                    try (ResultSet rs = stmt.executeQuery(q.getQuery())) {
                        StringBuilder result = new StringBuilder();
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();

                        while (rs.next()) {
                            for (int i = 1; i <= columnCount; i++) {
                                if (i > 1) result.append(", ");
                                result.append(rs.getString(i));
                            }
                            result.append("\n");
                        }
                        q.setExpectedResult(result.toString().trim());
                    }
                } else {
                    int affectedRows = stmt.executeUpdate(q.getQuery());
                    q.setExpectedResult("Affected rows: " + affectedRows);
                }

                log.debug("Executed query for question: {}", q.getText());

            } catch (SQLException e) {
                log.error("Failed to execute query for question: {}", q.getText(), e);
                q.setExpectedResult("ERROR: " + e.getMessage());
            }
        }

        return questions;
    }

    /**
     * Генерирует Moodle XML
     */
    public static String generateMoodleXml(List<Question> questions, String dbName, String categoryName) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<quiz>\n");

        xml.append("  <question type=\"category\">\n");
        xml.append("    <category>\n");
        xml.append("      <text>$course$/top/").append(escapeXml(categoryName)).append("</text>\n");
        xml.append("    </category>\n");
        xml.append("  </question>\n");

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            xml.append("  <question type=\"code\">\n");
            xml.append("    <name>\n");
            xml.append("      <text>").append(escapeXml("Вопрос " + (i + 1) + ": " + q.getText())).append("</text>\n");
            xml.append("    </name>\n");
            xml.append("    <questiontext format=\"html\">\n");
            xml.append("      <text><![CDATA[<p>").append(escapeXml(q.getText())).append("</p>");
            xml.append("<pre><code class=\"sql\">").append(escapeXml(q.getQuery())).append("</code></pre>");
            xml.append("]]></text>\n");
            xml.append("    </questiontext>\n");
            xml.append("    <language>sql</language>\n");

            if (q.getExpectedResult() != null && !q.getExpectedResult().isEmpty()) {
                xml.append("    <expected>\n");
                xml.append("      <text><![CDATA[").append(q.getExpectedResult()).append("]]></text>\n");
                xml.append("    </expected>\n");
            }

            xml.append("    <answer fraction=\"100\">\n");
            xml.append("      <text><![CDATA[").append(escapeXml(q.getQuery())).append("]]></text>\n");
            xml.append("    </answer>\n");

            xml.append("  </question>\n");
        }

        xml.append("</quiz>");
        return xml.toString();
    }

    /**
     * Генерирует GIFT формат (поддерживается Moodle из коробки)
     */
    public static String generateGiftFormat(List<Question> questions, String categoryName) {
        StringBuilder gift = new StringBuilder();

        // Категория
        gift.append("$CATEGORY: $course$/top/").append(escapeGift(categoryName)).append("\n\n");

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            gift.append("::").append(escapeGift("Вопрос " + (i + 1) + ": " + q.getText())).append("::\n");
            gift.append("[code lang=\"sql\"]\n");
            gift.append(q.getQuery()).append("\n");
            gift.append("[/code]\n");
            gift.append("{\n");
            gift.append("  =").append(escapeGift(q.getExpectedResult())).append("\n");
            gift.append("}\n\n");
        }

        return gift.toString();
    }

    /**
     * Генерирует простой текстовый формат (для просмотра)
     */
    public static String generateTextFormat(List<Question> questions) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            text.append("=").append("=").append("=").append("=").append("=").append("=").append("=").append("=").append("=").append("=").append("\n");
            text.append("ВОПРОС ").append(i + 1).append(": ").append(q.getText()).append("\n");
            text.append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("\n");
            text.append("SQL запрос:\n");
            text.append(q.getQuery()).append("\n");
            text.append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("-").append("\n");
            text.append("Ожидаемый результат:\n");
            text.append(q.getExpectedResult()).append("\n\n");
        }

        return text.toString();
    }

    /**
     * Экранирование XML специальных символов
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Экранирование для GIFT формата
     */
    private static String escapeGift(String text) {
        if (text == null) return "";
        return text.replace(":", "\\:")
                .replace("=", "\\=")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }
}