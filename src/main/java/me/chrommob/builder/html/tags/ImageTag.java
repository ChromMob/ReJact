package me.chrommob.builder.html.tags;

import static me.chrommob.builder.html.constants.GlobalAttributes.SRC;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import me.chrommob.builder.utils.ImageOptimiser;

public class ImageTag extends Tag {
    public record ReturnedImage(Tag tag, String link) {
    }

    public ImageTag() {
        super("img");
    }

    public CompletableFuture<ReturnedImage> setImage(byte[] imageData, boolean isGif, boolean isWebp) {
        return CompletableFuture.supplyAsync(() -> {
            String link = null;
            if (imageData != null) {
                link = ImageOptimiser.optimise(imageData, isGif, isWebp);
            }
            return new ReturnedImage(addAttribute(SRC, link), link);
        });
    }

    public CompletableFuture<ReturnedImage> setImage(String httpUrl) {
        try (InputStream inputStream = new BufferedInputStream(new URL(httpUrl).openStream())) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            byte[] bytes = outputStream.toByteArray();
            boolean isWebp = httpUrl.endsWith(".webp");
            boolean isGif = httpUrl.endsWith(".gif");
            return setImage(bytes, isGif, isWebp);
        } catch (IOException e) {
            e.printStackTrace();
        }
        CompletableFuture<ReturnedImage> future = new CompletableFuture<>();
        future.complete(new ReturnedImage(this, null));
        return future;
    }
}
