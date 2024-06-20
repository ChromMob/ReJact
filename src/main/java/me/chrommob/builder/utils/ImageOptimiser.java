package me.chrommob.builder.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            File file = new File(internalPath, path);
            ctx.contentType("image/webp");
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
                    image.resizeToWidth(w);
                }
            }
            if (q < 0) {
                q = 0;
            }
            if (q > 100) {
                q = 100;
            }
            bytes = image.bytes(WebpWriter.DEFAULT.withQ(q).withMultiThread());
            ctx.result(bytes).header("Cache-Control", "public, max-age=31536000");
        });
    }

    private static final File internalPath = new File("internal", "images");
    static {
        internalPath.mkdirs();
    }

    public static String optimise(byte[] imageData, boolean isGif, boolean isWebp) {
        String name = Internal.generateRandomString(20);


        File out = new File(internalPath, name + ".webp");

        if (isWebp) {
            FileUtils.writeFile(out, imageData);
            return "/_rejact/images/" + name + ".webp";
        }

        try {
            if (isGif) {
                AnimatedGif animatedGif = AnimatedGifReader.read(ImageSource.of(imageData));
                animatedGif.output(Gif2WebpWriter.DEFAULT, out);
            } else {
                ImmutableImage image = ImmutableImage.loader().withImageReaders(imageReaders).fromBytes(imageData);
                image.output(WebpWriter.DEFAULT.withMultiThread(), out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "/_rejact/images/" + name + ".webp";
    }
}
