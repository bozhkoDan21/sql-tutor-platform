package com.sqltrainer.servlet.log;

import com.sqltrainer.servlet.teacher.TeacherServlet;
import com.sqltrainer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Сервлет для потоковой передачи логов в реальном времени (Server-Sent Events).
 * Используется преподавателем для отслеживания прогресса выполнения SQL-скриптов
 * при загрузке новой базы данных.
 */
@WebServlet("/api/logs")
public class LogStreamServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LogStreamServlet.class);

    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static final int MAX_QUEUE_SIZE = Constants.LOG_QUEUE_MAX_SIZE;

    /**
     * Добавляет сообщение в очередь логов.
     * Вызывается из TeacherServlet во время выполнения SQL-скрипта.
     */
    public static void addLog(String message) {
        if (logQueue.size() >= MAX_QUEUE_SIZE) {
            log.warn("Log queue size limit reached, clearing old messages");
            logQueue.clear();
        }
        logQueue.offer(message);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Настройка Server-Sent Events
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("Keep-Alive", "timeout=30");

        PrintWriter writer = resp.getWriter();
        long lastCleanupTime = System.currentTimeMillis();

        while (true) {
            // Периодическая очистка очереди от старых сообщений
            if (System.currentTimeMillis() - lastCleanupTime > Constants.LOG_CLEANUP_INTERVAL_MS) {
                cleanupLogQueue();
                lastCleanupTime = System.currentTimeMillis();
            }

            String logMessage;
            try {
                logMessage = logQueue.poll(Constants.LOG_POLL_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Отправка heartbeat или сообщения клиенту
            if (logMessage == null) {
                writer.write(": heartbeat\n\n");
            } else {
                writer.write("data: " + logMessage + "\n\n");
            }
            writer.flush();

            // Проверяем, не закрыл ли клиент соединение
            if (writer.checkError()) {
                log.debug("Client disconnected from log stream");
                break;
            }
        }

        writer.close();
    }

    /**
     * Очищает очередь логов, если она превышает максимальный размер.
     * Удаляет половину старых сообщений.
     */
    private void cleanupLogQueue() {
        int sizeBefore = logQueue.size();
        if (sizeBefore > MAX_QUEUE_SIZE) {
            int toRemove = sizeBefore - (MAX_QUEUE_SIZE / 2);
            for (int i = 0; i < toRemove; i++) {
                logQueue.poll();
            }
            log.debug("Cleaned log queue: removed {} messages, remaining: {}", toRemove, logQueue.size());
        }
    }
}