package me.chrommob.builder.html.constants;

import java.util.Random;

public class Internal {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String EVERYTHING = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final String HEADER = "<!DOCTYPE html>\n";

    public static String generateRandomString(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        int index = random.nextInt(CHARACTERS.length());
        sb.append(CHARACTERS.charAt(index));
        for (int i = 0; i < length - 1; i++) {
            int index2 = random.nextInt(EVERYTHING.length());
            sb.append(EVERYTHING.charAt(index2));
        }
        return sb.toString();
    }

}
