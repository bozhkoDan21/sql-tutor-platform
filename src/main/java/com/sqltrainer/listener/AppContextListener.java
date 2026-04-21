package com.sqltrainer.listener;

import com.sqltrainer.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Слушатель контекста приложения.
 * Обеспечивает корректное закрытие ресурсов при остановке приложения.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("SQL Trainer application started");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("SQL Trainer application stopping, closing database pools...");
        DatabaseConfig.closeAllPools();
        log.info("Database pools closed successfully");
    }
}