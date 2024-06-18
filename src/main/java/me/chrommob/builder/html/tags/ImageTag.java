package me.chrommob.builder.html.tags;

import static me.chrommob.builder.html.constants.GlobalAttributes.SRC;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import me.chrommob.builder.utils.ImageOptimiser;

public class ImageTag extends Tag {
    enum ImageType {
        BASE64,
        FILE
    }

    public ImageTag() {
        super("img");
    }

    public Tag setImage(byte[] imageData, boolean isGif) {
        String link = ImageOptimiser.optimise(imageData, isGif);
        return addAttribute(SRC, link);
    }

    public Tag setImage(String httpUrl) {
        try (InputStream inputStream = new URL(httpUrl).openStream()) {
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            return setImage(bytes, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }
}
