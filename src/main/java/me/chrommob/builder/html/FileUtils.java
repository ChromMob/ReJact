package me.chrommob.builder.html;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

public class FileUtils {
    public static String readFileToString(File file) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static byte[] readFileToBytes(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void writeFile(File file, byte[] bytes) {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
