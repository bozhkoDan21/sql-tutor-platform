package com.sqltutor.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@WebServlet("/api/logs")
public class LogStreamServlet extends HttpServlet {

    // Хранилище логов для каждой сессии
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    public static void addLog(String message) {
        logQueue.offer(message);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        PrintWriter writer = resp.getWriter();

        // Отправляем логи в реальном времени
        while (true) {
            try {
                String log = logQueue.take(); // ждем новые логи
                writer.write("data: " + log + "\n\n");
                writer.flush();

                if (log.contains("completed successfully")) {
                    Thread.sleep(100); // небольшая задержка
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}