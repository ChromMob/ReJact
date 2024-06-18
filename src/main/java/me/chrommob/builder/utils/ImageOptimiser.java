package me.chrommob.builder.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            ctx.contentType("image/png");
            byte[] bytes = FileUtils.readFileToBytes(file);
            ctx.result(bytes);
        });
    }

    private static final File internalPath = new File("internal", "images");
    static {
        internalPath.mkdirs();
    }

    public static String optimise(byte[] imageData, boolean isGif) {
        String name = Internal.generateRandomString(20);

        File out = new File(internalPath, name + ".webp");
        try {
            List<ImageReader> imageReaders = new ArrayList<>();
            imageReaders.add(new PngReader());
            imageReaders.add(new ImageIOReader());
            imageReaders.add(new OpenGifReader());
            imageReaders.add(new WebpImageReader());
            if (isGif) {
                AnimatedGif animatedGif = AnimatedGifReader.read(ImageSource.of(imageData));
                animatedGif.output(Gif2WebpWriter.DEFAULT, out);
            } else {
                ImmutableImage image = ImmutableImage.loader().withImageReaders(imageReaders).fromBytes(imageData);
                image.output(WebpWriter.DEFAULT, out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "/_rejact/images/" + name + ".webp";
    }
}
