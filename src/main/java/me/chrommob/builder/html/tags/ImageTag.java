package me.chrommob.builder.html.tags;

import static me.chrommob.builder.html.constants.GlobalAttributes.SRC;

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
        try (InputStream inputStream = new URL(httpUrl).openStream()) {
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
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
