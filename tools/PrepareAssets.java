import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Build tool: resizes the generated concept art in place so the demo stays light on phones.
 * Runs on the JDK only (ImageIO), no external tools. javac tools/PrepareAssets.java && java -cp tools PrepareAssets assets
 */
public final class PrepareAssets {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "assets";
        toJpeg(dir + "/hero.png", dir + "/hero.jpg", 880);
        resize(dir + "/empty.png", 400);
        resize(dir + "/avatar-robot.png", 128);
        resize(dir + "/avatar-fox.png", 128);
    }

    private static void toJpeg(String path, String out, int width) throws Exception {
        BufferedImage source = ImageIO.read(new File(path));
        BufferedImage img = scale(source, width);
        if (img == null) {
            img = source;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ImageIO.write(rgb, "jpg", new File(out));
        System.out.println(out + " (" + new File(out).length() / 1024 + " KB)");
    }

    private static void resize(String path, int width) throws Exception {
        File file = new File(path);
        BufferedImage scaled = scale(ImageIO.read(file), width);
        if (scaled == null) {
            System.out.println(path + " unchanged");
            return;
        }
        ImageIO.write(scaled, "png", file);
        System.out.println(path + " -> " + scaled.getWidth() + "x" + scaled.getHeight() + " ("
                + file.length() / 1024 + " KB)");
    }

    private static BufferedImage scale(BufferedImage img, int width) {
        if (img == null || img.getWidth() <= width) {
            return null;
        }
        int height = img.getHeight() * width / img.getWidth();
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }
}
