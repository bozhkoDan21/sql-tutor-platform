package com.sqltrainer.util;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Scanner;

/**
 * Простая утилита для генерации BCrypt-хеша пароля.
 * Используется для создания хеша для teacher_settings таблицы.
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("========================================");
        System.out.println("  BCrypt Password Hash Generator");
        System.out.println("========================================");
        System.out.println();

        System.out.print("Введите пароль для хеширования: ");
        String password = scanner.nextLine();

        if (password == null || password.isEmpty()) {
            System.out.println("Ошибка: пароль не может быть пустым");
            return;
        }

        // Генерируем хеш
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        System.out.println();
        System.out.println("========================================");
        System.out.println("Ваш BCrypt-хеш:");
        System.out.println(hashedPassword);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Скопируйте эту строку в setup_metadata.sql:");
        System.out.println();
        System.out.println("INSERT INTO teacher_settings (setting_key, setting_value)");
        System.out.println("VALUES ('password', '" + hashedPassword + "')");
        System.out.println("ON CONFLICT (setting_key) DO NOTHING;");

        scanner.close();
    }
}