package com.sqltrainer.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Утилита для генерации BCrypt-хеша пароля.
 * Используется для создания хеша для teacher_settings таблицы.
 *
 * Использование:
 *   java com.sqltrainer.util.PasswordHashGenerator <пароль>
 *
 * Пример:
 *   java com.sqltrainer.util.PasswordHashGenerator teacher123
 *
 * Вывод:
 *   $2a$10$PG7vidujC0iL05PyGiOzw.rxCoVHdZWZyWzmOG4dpBRJ8knP6GZMa
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        // Если передан аргумент командной строки - используем его
        if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            String password = args[0];
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            System.out.println(hashedPassword);
            return;
        }

        // Если аргумент не передан - выводим справку
        System.out.println("========================================");
        System.out.println("  BCrypt Password Hash Generator");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Использование: java com.sqltrainer.util.PasswordHashGenerator <пароль>");
        System.out.println("Пример: java com.sqltrainer.util.PasswordHashGenerator teacher123");
        System.out.println();
        System.out.println("Вывод: BCrypt-хеш пароля");
        System.out.println("========================================");
    }
}