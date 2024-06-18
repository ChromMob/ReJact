package me.chrommob.builder.utils;

import java.util.List;
import java.util.Random;

public class Internal {
    public enum SIZE_UNITS {
        B(1),
        KB(1024),
        MB(1024 * 1024),
        GB(1024 * 1024 * 1024);

        private final int size;

        SIZE_UNITS(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }
    public static final List<String> IMAGE_TYPES = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg");
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

    public static boolean isImage(String type) {
        return IMAGE_TYPES.stream().anyMatch(type::contains);
    }

    public static String sanitise(String html) {
        String removedTags = html.replaceAll("<[^>]*>", "");
        StringBuilder sanitized = new StringBuilder();
        for (char c : removedTags.toCharArray()) {
            switch (c) {
                case '<':
                    sanitized.append("&lt;");
                    break;
                case '>':
                    sanitized.append("&gt;");
                    break;
                case '&':
                    sanitized.append("&amp;");
                    break;
                case '"':
                    sanitized.append("&quot;");
                    break;
                case '\'':
                    sanitized.append("&#x27;");
                    break;
                case '/':
                    sanitized.append("&#x2F;");
                    break;
                case '\\':
                    sanitized.append("&#x5C;");
                    break;
                default:
                    sanitized.append(c);
            }
        }
        return sanitized.toString();
    }
}
