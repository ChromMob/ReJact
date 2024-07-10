package me.chrommob.builder.utils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.ImageIOReader;
import com.sksamuel.scrimage.nio.ImageReader;
import com.sksamuel.scrimage.nio.ImageSource;
import com.sksamuel.scrimage.nio.OpenGifReader;
import com.sksamuel.scrimage.nio.PngReader;
import com.sksamuel.scrimage.webp.Gif2WebpWriter;
import com.sksamuel.scrimage.webp.WebpImageReader;
import com.sksamuel.scrimage.webp.WebpWriter;

import io.javalin.Javalin;
import me.chrommob.builder.html.FileUtils;

public class ImageOptimiser {
    public record ImageSize(int width, int height) {}
    private static final Map<String, byte[]> imageCache = new HashMap<>();
    private static final TreeMap<Long, String> imageCacheKeys = new TreeMap<>();
    private static final Map<String, Long> imageCacheTime = new HashMap<>();
    private static final Map<String, ImageSize> imageSizes = new HashMap<>();
    private static final List<ImageReader> imageReaders = new ArrayList<>();
    static {
            imageReaders.add(new PngReader());
            imageReaders.add(new ImageIOReader());
            imageReaders.add(new OpenGifReader());
            imageReaders.add(new WebpImageReader());
    }

    private final Javalin app;

    public ImageOptimiser(Javalin app) {
        this.app = app;
        runServer();
    }

    private void runServer() {
        app.get("/_rejact/images/*", ctx -> {
            String path = ctx.path();
            if (path.startsWith("/_rejact/images/")) {
                path = path.substring("/_rejact/images/".length());
            }
            ctx.contentType("image/webp");
            if (imageCache.containsKey(path)) {
                long time = imageCacheTime.get(path);
                imageCacheKeys.remove(time);
                imageCacheTime.remove(path);
                imageCacheKeys.put(System.currentTimeMillis(), path);
                imageCacheTime.put(path, System.currentTimeMillis());
                ctx.result(imageCache.get(path)).header("Cache-Control", "public, max-age=31536000");
                return;
            }
            File file = new File(internalPath, path);
            byte[] bytes = FileUtils.readFileToBytes(file);
            if (bytes == null) {
                ctx.status(404);
                return;
            }
            String args = ctx.queryString();
            int q = 100;
            int w = -1;
            if (args != null) {
                String[] data = args.split("&");
                for (String arg : data) {
                    String[] keyValue = arg.split("=");
                    if (keyValue.length != 2) {
                        continue;
                    }
                    String key = keyValue[0];
                    String value = keyValue[1];
                    if (key.equals("q")) {
                        try {
                            q = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            q = 100;
                        }
                    } else if (key.equals("w")) {
                        try {
                            w = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            w = -1;
                        }
                    }
                }
            }
            if (w == -1 && q == 100) {
                ctx.result(bytes).header("Cache-Control", "public, max-age=31536000");
                return;
            }
            ImmutableImage image = ImmutableImage.loader().withImageReaders(List.of(new WebpImageReader())).fromBytes(bytes);
            if (w != -1) {
                if (w < image.width && w > 0) {
                    if (w < 10) {
                        w = 10;
                    }
                    int h = (int) ((double) w / image.width * image.height);
                    if (h < 10) {
                        //adjust w so h is at least 3
                        h = 10;
                        w = (int) ((double) h / image.height * image.width);
                    }
                    image = image.scaleToWidth(w);
                }
            }
            if (q < 0) {
                q = 0;
            }
            if (q > 100) {
                q = 100;
            }
            bytes = image.bytes(WebpWriter.DEFAULT.withQ(q).withMultiThread());
            if (imageCache.size() > 100) {
                long oldestTime = imageCacheKeys.firstKey();
                String oldestPath = imageCacheKeys.get(oldestTime);
                imageCache.remove(oldestPath);
                imageCacheKeys.remove(oldestTime);
                imageCacheTime.remove(oldestPath);
            }
            imageCache.put(path, bytes);
            long time = System.currentTimeMillis();
            imageCacheKeys.put(time, path);
            imageCacheTime.put(path, time);
            ctx.result(bytes).header("Cache-Control", "public, max-age=31536000");
        });
    }

    private static final File internalPath = new File("internal", "images");
    static {
        internalPath.mkdirs();
    }

    public static String generateInternalUrl(byte[] imageData, boolean isGif) {
        String name = Internal.generateRandomString(20);


        File out = new File(internalPath, name + ".webp");

        try {
            if (isGif) {
                AnimatedGif animatedGif = AnimatedGifReader.read(ImageSource.of(imageData));
                imageSizes.put(name, new ImageSize(animatedGif.getDimensions().width, animatedGif.getDimensions().height));
                animatedGif.output(Gif2WebpWriter.DEFAULT, out);
            } else {
                ImmutableImage image = ImmutableImage.loader().withImageReaders(imageReaders).fromBytes(imageData);
                imageSizes.put(name, new ImageSize(image.width, image.height));
                image.output(WebpWriter.DEFAULT.withMultiThread(), out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "/_rejact/images/" + name + ".webp";
    }

    public static ImageSize getImageSize(String name) {
        return imageSizes.get(name);
    }
}
