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
 *
 * Принцип работы:
 * 1. Преподаватель создаёт текстовый файл с парами "вопрос" + "SQL запрос"
 * 2. Система выполняет эталонные запросы к учебной БД
 * 3. Для SELECT-запросов вычисляется SHA-256 хеш результата (16 символов)
 * 4. Генерируется GIFT/XML файл для импорта в Moodle
 * 5. В Moodle правильным ответом считается хеш, введённый студентом
 *
 * Проверка ответа студента:
 * - Студент выполняет запрос в SQL Trainer
 * - Система показывает хеш результата (16 символов)
 * - Студент копирует хеш в Moodle
 * - Moodle сравнивает введённый хеш с эталонным
 * - При несовпадении преподаватель может экспортировать CSV и сравнить детально
 */
public class MoodleGenerator {
    private static final Logger log = LoggerFactory.getLogger(MoodleGenerator.class);

    // Максимальное количество строк, используемых для вычисления хеша
    // (предотвращает проблему с памятью при очень больших результатах)
    private static final int MAX_ROWS_FOR_HASH = 10000;

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
     *
     * Пример входного файла:
     * Найти всех студентов, родившихся после 2000 года
     * SELECT * FROM student WHERE birth_date > '2000-01-01' LIMIT 10;
     *
     * Посчитать количество студентов в каждом городе
     * SELECT city_id, COUNT(*) FROM student GROUP BY city_id;
     *
     * @param inputStream поток входного файла
     * @return список вопросов
     * @throws IOException если файл имеет неверный формат
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
     *
     * Для SELECT-запросов:
     * - Выполняется запрос к учебной БД
     * - Результат преобразуется в строку вида "value1|value2|value3\n..."
     * - Вычисляется SHA-256 хеш от этой строки
     * - Хеш усекается до 16 символов для удобства ввода студентом
     *
     * Для UPDATE/INSERT/DELETE:
     * - Выполняется запрос
     * - Сохраняется количество затронутых строк
     *
     * @param questions список вопросов
     * @param dbName имя базы данных
     * @return список вопросов с заполненными эталонными результатами
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

                        StringBuilder hashBuilder = new StringBuilder();
                        StringBuilder resultBuilder = new StringBuilder();
                        int rowCount = 0;

                        // Ограничиваем количество строк для хеша, чтобы избежать проблем с памятью
                        while (rs.next() && rowCount < MAX_ROWS_FOR_HASH) {
                            rowCount++;
                            for (int i = 1; i <= columnCount; i++) {
                                if (i > 1) {
                                    hashBuilder.append("|");
                                    if (resultBuilder.length() > 0) resultBuilder.append(", ");
                                }
                                String value = rs.getString(i);
                                if (value == null) value = "NULL";
                                hashBuilder.append(value);
                                resultBuilder.append(value);
                            }
                            hashBuilder.append("\n");
                            resultBuilder.append("\n");
                        }

                        // Если строк больше чем MAX_ROWS_FOR_HASH, добавляем предупреждение
                        if (rowCount >= MAX_ROWS_FOR_HASH) {
                            // Продолжаем чтение, чтобы узнать точное количество строк
                            while (rs.next()) {
                                rowCount++;
                            }
                            log.warn("Query result truncated for hash: {} rows total, using first {} rows",
                                    rowCount, MAX_ROWS_FOR_HASH);
                        }

                        q.setRowCount(rowCount);
                        q.setExpectedResult(resultBuilder.toString().trim());

                        // Генерируем SHA-256 хеш от результата (усечённый до 16 символов)
                        if (rowCount > 0) {
                            String fullHash = calculateSha256(hashBuilder.toString().trim());
                            q.setExpectedHash(fullHash.substring(0, Math.min(16, fullHash.length())));
                        } else {
                            q.setExpectedHash(calculateSha256("EMPTY_RESULT").substring(0, 16));
                        }

                        log.debug("Query returned {} rows, hash: {}", rowCount, q.getExpectedHash());
                    }
                } else {
                    int affectedRows = stmt.executeUpdate(q.getQuery());
                    q.setRowCount(affectedRows);
                    q.setExpectedResult("Затронуто строк: " + affectedRows);
                    q.setExpectedHash(calculateSha256("UPDATE:" + affectedRows).substring(0, 16));
                }

            } catch (SQLException e) {
                log.error("Failed to execute query for question: {}", q.getText(), e);
                q.setExpectedResult("ERROR: " + e.getMessage());
                q.setExpectedHash(calculateSha256("ERROR:" + e.getMessage()).substring(0, 16));
            }
        }

        return questions;
    }

    /**
     * Вычисляет SHA-256 хеш строки.
     *
     * @param input входная строка
     * @return хеш в шестнадцатеричном формате (64 символа)
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
     *
     * Формат GIFT (General Import Format Technology):
     * - $CATEGORY: задаёт категорию вопросов в Moodle
     * - ::название вопроса:: — заголовок
     * - [code lang="sql"] — блок кода с SQL запросом
     * - { =правильный_ответ } — блок ответов
     * - ~# — разделитель для комментария
     *
     * Для SELECT-запросов правильным ответом является SHA-256 хеш результата (16 символов).
     * Преподаватель должен объяснить студентам, что нужно копировать хеш из SQL Trainer.
     *
     * @param questions список вопросов
     * @param categoryName название категории в Moodle
     * @return строка в формате GIFT
     */
    public static String generateGiftFormat(List<Question> questions, String categoryName) {
        StringBuilder gift = new StringBuilder();

        // Категория для импорта в Moodle
        gift.append("$CATEGORY: $course$/top/").append(escapeGift(categoryName)).append("\n\n");

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String questionTitle = "Вопрос " + (i + 1) + ": " + q.getText();

            gift.append("::").append(escapeGift(questionTitle)).append("::\n");
            gift.append("[code lang=\"sql\"]\n");
            gift.append(q.getQuery()).append("\n");
            gift.append("[/code]\n");
            gift.append("{\n");

            if (q.isSelect() && q.getRowCount() > 0) {
                // Определяем тип ответа и формируем правильный ответ
                String answer = determineAnswerType(q);
                gift.append("  ").append(answer).append("\n");

                // Добавляем комментарий только для хеша (не перегружаем простые ответы)
                if (answer.startsWith("=") && answer.length() == 17) { // = + 16 символов хеша
                    gift.append("  ~#\n");
                    gift.append("  Комментарий для проверяющего:\n");
                    gift.append("  ----------------------------------------\n");
                    gift.append("  Ожидаемый хеш результата: ").append(q.getExpectedHash()).append("\n");
                    gift.append("  Количество строк в результате: ").append(q.getRowCount()).append("\n");
                    gift.append("  \n");
                    gift.append("  КАК ПРОВЕРЯТЬ ОТВЕТ СТУДЕНТА:\n");
                    gift.append("  1. Студент выполняет запрос в SQL Trainer\n");
                    gift.append("  2. SQL Trainer показывает хеш результата (16 символов)\n");
                    gift.append("  3. Студент копирует этот хеш в Moodle\n");
                    gift.append("  4. Moodle сравнивает хеш студента с эталонным\n");
                    gift.append("  5. Если хеши не совпадают, экспортируйте результат студента в CSV\n");
                    gift.append("     и сравните с ожидаемым результатом (см. CSV-файл)\n");
                    gift.append("  ----------------------------------------\n");
                }
            } else if (!q.isSelect()) {
                // Для не-SELECT запросов используем текстовый ответ
                gift.append("  =").append(escapeGift(q.getExpectedResult())).append("\n");
            } else {
                // SELECT с пустым результатом
                gift.append("  =").append(escapeGift("Нет данных")).append("\n");
            }

            gift.append("}\n\n");
        }

        return gift.toString();
    }

    /**
     * Определяет тип ответа для SELECT-запроса.
     *
     * Типы ответов:
     * 1. Одно целое число (например, COUNT(*)) → точное число
     * 2. Одно число с плавающей точкой (например, AVG(...)) → число с погрешностью ±0,5%
     * 3. Одно текстовое значение → текстовая строка
     * 4. Много строк или много колонок → SHA-256 хеш (16 символов)
     *
     * @param q вопрос с выполненным запросом
     * @return строка с правильным ответом в формате GIFT
     */
    private static String determineAnswerType(Question q) {
        String result = q.getExpectedResult();
        if (result == null || result.isEmpty()) {
            return "=" + q.getExpectedHash();
        }

        String trimmed = result.trim();

        // Проверка: одно число с плавающей точкой (например, 1250.5)
        if (trimmed.matches("^-?\\d+\\.\\d+$")) {
            double value = Double.parseDouble(trimmed);
            // GIFT поддерживает погрешность в процентах: =1250.5:0.5%
            return "=" + value + ":0.5%";
        }

        // Проверка: одно целое число (например, 245)
        if (trimmed.matches("^-?\\d+$")) {
            return "=" + trimmed;
        }

        // Проверка: одно текстовое значение (не число, не содержит переносов строк, не содержит разделителей)
        if (!trimmed.contains("\n") && !trimmed.contains("|")) {
            return "=" + escapeGift(trimmed);
        }

        // Иначе — хеш (много строк или много колонок)
        return "=" + q.getExpectedHash();
    }

    /**
     * Генерирует Moodle XML формат.
     *
     * Формат XML для плагина CodeRunner (требует установки в Moodle).
     * Позволяет выполнять SQL-запросы непосредственно в Moodle.
     *
     * @param questions список вопросов
     * @param dbName имя базы данных (для информации)
     * @param categoryName название категории
     * @return строка в формате Moodle XML
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
     * Генерирует текстовый формат для предварительного просмотра.
     * Показывает первые 20 строк результата и хеш.
     *
     * @param questions список вопросов
     * @return строка в текстовом формате
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
                text.append("SHA-256 хеш результата (16 символов): ").append(q.getExpectedHash()).append("\n");
                text.append("----------------------------------------\n");
                text.append("Первые 20 строк результата:\n");
                String[] lines = q.getExpectedResult().split("\n");
                for (int j = 0; j < Math.min(20, lines.length); j++) {
                    text.append("  ").append(lines[j]).append("\n");
                }
                if (lines.length > 20) {
                    text.append("  ... и ещё ").append(lines.length - 20).append(" строк\n");
                }
                text.append("\n");
                text.append("Для проверки ответа студента:\n");
                text.append("1. Выполните запрос студента в SQL Trainer\n");
                text.append("2. Сравните полученный хеш с ").append(q.getExpectedHash()).append("\n");
                text.append("3. При несовпадении экспортируйте результат в CSV\n");
            } else {
                text.append("Результат: ").append(q.getExpectedResult()).append("\n");
            }

            text.append("\n");
        }

        return text.toString();
    }

    /**
     * Экранирует специальные символы для XML.
     *
     * @param text текст для экранирования
     * @return экранированный текст
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
     * Экранирует специальные символы для GIFT формата.
     * Символы : = { } [ ] должны быть экранированы обратным слешем.
     *
     * @param text текст для экранирования
     * @return экранированный текст
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