import java.awt.image.*;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.Random;

public class CreateTestImage {
    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(42);
        
        // Region 1 (top-left): dark grey (~50)
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                int grey = 40 + rand.nextInt(30);
                int rgb = (grey << 16) | (grey << 8) | grey;
                img.setRGB(x, y, rgb);
            }
        }
        
        // Region 2 (top-right): mid grey (~130)
        for (int y = 0; y < 100; y++) {
            for (int x = 100; x < 200; x++) {
                int grey = 120 + rand.nextInt(30);
                int rgb = (grey << 16) | (grey << 8) | grey;
                img.setRGB(x, y, rgb);
            }
        }
        
        // Region 3 (bottom): bright grey (~210)
        for (int y = 100; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                int grey = 200 + rand.nextInt(30);
                int rgb = (grey << 16) | (grey << 8) | grey;
                img.setRGB(x, y, rgb);
            }
        }
        
        ImageIO.write(img, "png", new File("test_image.png"));
        System.out.println("✓ Created test image: test_image.png (200×200 pixels)");
        System.out.println("  - Top-left (0-99, 0-99): dark grey (~50)");
        System.out.println("  - Top-right (100-199, 0-99): mid grey (~130)");
        System.out.println("  - Bottom (0-199, 100-199): bright grey (~210)");
    }
}
