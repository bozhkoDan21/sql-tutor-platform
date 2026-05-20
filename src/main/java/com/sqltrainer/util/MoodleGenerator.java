package com.sqltrainer.util;

import com.sqltrainer.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Генератор вопросов для Moodle в форматах GIFT и XML.
 * Для табличных ответов используется SHA-256 хеш результата запроса.
 */
public class MoodleGenerator {
    private static final Logger log = LoggerFactory.getLogger(MoodleGenerator.class);

    /**
     * Структура вопроса.
     */
    public static class Question {
        private final String text;
        private final String query;
        private String expectedResult;
        private String expectedHash;
        private int rowCount;
        private boolean isSelect;

        public Question(String text, String query) {
            this.text = text;
            this.query = query;
        }

        public String getText() { return text; }
        public String getQuery() { return query; }
        public String getExpectedResult() { return expectedResult; }
        public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
        public String getExpectedHash() { return expectedHash; }
        public void setExpectedHash(String expectedHash) { this.expectedHash = expectedHash; }
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
        public boolean isSelect() { return isSelect; }
        public void setSelect(boolean select) { isSelect = select; }
    }

    /**
     * Парсит входной файл с заданиями.
     * Формат: каждая пара строк: текст вопроса, затем SQL запрос.
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
                throw new IOException("Файл должен содержать пары строк: текст вопроса и SQL запрос");
            }

            for (int i = 0; i < lines.size(); i += 2) {
                String questionText = lines.get(i);
                String sqlQuery = lines.get(i + 1);

                if (sqlQuery.isEmpty()) {
                    throw new IOException("SQL запрос не может быть пустым для вопроса: " + questionText);
                }

                questions.add(new Question(questionText, sqlQuery));
            }
        }

        return questions;
    }

    /**
     * Выполняет запросы и получает эталонные результаты и хеши.
     */
    public static List<Question> executeQueries(List<Question> questions, String dbName) {
        for (Question q : questions) {
            try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
                 Statement stmt = conn.createStatement()) {

                stmt.setQueryTimeout(30);

                String sqlTrimmed = q.getQuery().trim().toLowerCase();
                q.setSelect(sqlTrimmed.startsWith("select"));

                if (q.isSelect()) {
                    try (ResultSet rs = stmt.executeQuery(q.getQuery())) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();

                        StringBuilder resultBuilder = new StringBuilder();
                        StringBuilder hashBuilder = new StringBuilder();
                        int rowCount = 0;

                        while (rs.next()) {
                            rowCount++;
                            for (int i = 1; i <= columnCount; i++) {
                                if (i > 1) {
                                    resultBuilder.append(", ");
                                    hashBuilder.append("|");
                                }
                                String value = rs.getString(i);
                                if (value == null) value = "NULL";
                                resultBuilder.append(value);
                                hashBuilder.append(value);
                            }
                            resultBuilder.append("\n");
                            hashBuilder.append("\n");
                        }

                        q.setRowCount(rowCount);
                        q.setExpectedResult(resultBuilder.toString().trim());

                        // Генерируем SHA-256 хеш от результата
                        if (rowCount > 0) {
                            q.setExpectedHash(calculateSha256(hashBuilder.toString().trim()));
                        } else {
                            q.setExpectedHash(calculateSha256("EMPTY_RESULT"));
                        }

                        log.debug("Query returned {} rows, hash: {}", rowCount, q.getExpectedHash());
                    }
                } else {
                    int affectedRows = stmt.executeUpdate(q.getQuery());
                    q.setRowCount(affectedRows);
                    q.setExpectedResult("Затронуто строк: " + affectedRows);
                    q.setExpectedHash(calculateSha256("UPDATE:" + affectedRows));
                }

            } catch (SQLException e) {
                log.error("Failed to execute query for question: {}", q.getText(), e);
                q.setExpectedResult("ERROR: " + e.getMessage());
                q.setExpectedHash(calculateSha256("ERROR:" + e.getMessage()));
            }
        }

        return questions;
    }

    /**
     * Вычисляет SHA-256 хеш строки.
     */
    private static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, using fallback");
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Генерирует GIFT формат с хешами в качестве правильных ответов.
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

            if (q.isSelect() && q.getRowCount() > 0) {
                // Для SELECT запросов используем хеш
                gift.append("  =").append(q.getExpectedHash()).append("\n");
                gift.append("  ~#\n");
                gift.append("  Комментарий для преподавателя:\n");
                gift.append("  Запрос вернул ").append(q.getRowCount()).append(" строк.\n");
                gift.append("  Полный результат можно посмотреть в CSV файле.\n");
            } else {
                // Для не-SELECT запросов используем текстовый ответ
                gift.append("  =").append(escapeGift(q.getExpectedResult())).append("\n");
            }

            gift.append("}\n\n");
        }

        return gift.toString();
    }

    /**
     * Генерирует Moodle XML формат.
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

            if (q.isSelect() && q.getRowCount() > 0) {
                xml.append("    <expected>\n");
                xml.append("      <text><![CDATA[").append(q.getExpectedHash()).append("]]></text>\n");
                xml.append("    </expected>\n");
                xml.append("    <comment>\n");
                xml.append("      <text>Запрос вернул ").append(q.getRowCount()).append(" строк. Хеш результата: ").append(q.getExpectedHash()).append("</text>\n");
                xml.append("    </comment>\n");
            } else {
                xml.append("    <expected>\n");
                xml.append("      <text><![CDATA[").append(escapeXml(q.getExpectedResult())).append("]]></text>\n");
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
     * Генерирует текстовый формат для просмотра.
     */
    public static String generateTextFormat(List<Question> questions) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            text.append("========================================\n");
            text.append("ВОПРОС ").append(i + 1).append(": ").append(q.getText()).append("\n");
            text.append("----------------------------------------\n");
            text.append("SQL запрос:\n");
            text.append(q.getQuery()).append("\n");
            text.append("----------------------------------------\n");

            if (q.isSelect()) {
                text.append("Количество строк: ").append(q.getRowCount()).append("\n");
                text.append("SHA-256 хеш результата: ").append(q.getExpectedHash()).append("\n");
                text.append("----------------------------------------\n");
                text.append("Первые 20 строк результата:\n");
                String[] lines = q.getExpectedResult().split("\n");
                for (int j = 0; j < Math.min(20, lines.length); j++) {
                    text.append("  ").append(lines[j]).append("\n");
                }
                if (lines.length > 20) {
                    text.append("  ... и ещё ").append(lines.length - 20).append(" строк\n");
                }
            } else {
                text.append("Результат: ").append(q.getExpectedResult()).append("\n");
            }

            text.append("\n");
        }

        return text.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

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