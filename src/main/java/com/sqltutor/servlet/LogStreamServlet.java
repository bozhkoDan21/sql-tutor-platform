package com.sqltutor.servlet;

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
 * <p>
 * Используется преподавателем для отслеживания прогресса выполнения SQL-скриптов
 * при загрузке новой базы данных. Поддерживает heartbeat для предотвращения
 * разрыва соединения.
 * </p>
 *
 * @see TeacherServlet
 */
@WebServlet("/api/logs")
public class LogStreamServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LogStreamServlet.class);

    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static final int MAX_QUEUE_SIZE = 10000;

    public static void addLog(String message) {
        if (logQueue.size() >= MAX_QUEUE_SIZE) {
            log.warn("Log queue size limit reached, clearing old messages");
            logQueue.clear();
        }
        logQueue.offer(message);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("Keep-Alive", "timeout=30");

        PrintWriter writer = resp.getWriter();

        long lastCleanupTime = System.currentTimeMillis();

        while (true) {
            // Периодическая очистка очереди
            if (System.currentTimeMillis() - lastCleanupTime > 60000) {
                cleanupLogQueue();
                lastCleanupTime = System.currentTimeMillis();
            }

            String logMessage;
            try {
                logMessage = logQueue.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Отправка сообщения
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