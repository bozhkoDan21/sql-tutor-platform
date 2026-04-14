package com.sqltrainer.util;
import org.mindrot.jbcrypt.BCrypt;

public class GenerateHash {
    public static void main(String[] args) {
        String password = "teacher123";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        System.out.println("Hash: " + hash);
    }
}
