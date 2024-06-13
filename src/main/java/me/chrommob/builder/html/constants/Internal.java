package me.chrommob.builder.html.constants;

import java.util.List;
import java.util.Random;

public class Internal {
    public static final List<String> CHARS = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
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
