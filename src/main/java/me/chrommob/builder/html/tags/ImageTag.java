package me.chrommob.builder.html.tags;

import static me.chrommob.builder.html.constants.GlobalAttributes.SRC;
import static me.chrommob.builder.html.constants.GlobalAttributes.SRCSET;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.chrommob.builder.utils.ImageOptimiser;

public class ImageTag extends Tag {
    private static final int[] widths = {320, 375, 414, 512, 640, 768, 800, 912, 1024, 1280, 1366, 1440, 1536, 1600, 1920, 2048, 2560, 3200, 3840 };
    public ImageTag() {
        super("img");
    }

    public CompletableFuture<Tag> setImage(byte[] imageData, boolean isGif) {
        return CompletableFuture.supplyAsync(() -> {
            String link = null;
            if (imageData != null) {
                link = ImageOptimiser.generateInternalUrl(imageData, isGif);
            }
            return setInternalImageUrl(link);
        });
    }

    public Tag setInternalImageUrl(String link) {
        List<String> srcset = new ArrayList<>();
        int quality = 75;
        String name = link.substring(link.lastIndexOf("/") + 1);
        ImageOptimiser.ImageSize imageSize = ImageOptimiser.getImageSize(name.replace(".webp", ""));
        int imageWidth;
        if (imageSize == null) {
            imageWidth = 0;
        } else {
            imageWidth = imageSize.width();
        }
        for (int width : widths) {
            if (width > imageWidth) {
                break;
            }
            srcset.add(String.format("%s?q=%d&w=%d %dw", link, quality, width, width));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < srcset.size(); i++) {
            boolean isLast = i == srcset.size() - 1;
            sb.append(srcset.get(i));
            if (!isLast) {
                sb.append(", ");
            }
        }
        addAttribute(SRCSET, sb.toString());
        addAttribute(SRC, link);
        return this;
    }

    public CompletableFuture<Tag> setImage(String httpUrl) {
        try (InputStream inputStream = new BufferedInputStream(new URL(httpUrl).openStream())) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            byte[] bytes = outputStream.toByteArray();
            boolean isGif = httpUrl.endsWith(".gif");
            return setImage(bytes, isGif);
        } catch (IOException e) {
            e.printStackTrace();
        }
        CompletableFuture<Tag> future = new CompletableFuture<>();
        future.complete(this);
        return future;
    }
}
