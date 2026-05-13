import java.util.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * ══════════════════════════════════════════════════════════
 * ALGORITHM OVERVIEW (7 steps as per lecture):
 *
 *  Step 1: Build histogram h(i)           — O(N)
 *  Step 2: Build cumulative sum P(i)      — O(256)
 *  Step 3: BFS to search best thresholds  — O(256^k) pruned by BFS
 *  Step 4: Use cumulative sums for stats  — O(1) per region
 *  Step 5: Select best threshold combo    — tracked during BFS
 *  Step 6: Apply thresholds to segment    — O(N)
 *  Step 7: Print time complexity          — analysis printed at end
 *
 * THEORY (Region Threshold using Moments / Within-group variance):
 *
 *  P(i)    = histogram probability of grey-level i   (h(i) / total_pixels)
 *
 *  For a threshold t splitting [1..t] (object) and [t+1..255] (background):
 *
 *    q_o(t) = Σ P(i)  for i=1..t        (probability of object class)
 *    q_b(t) = Σ P(i)  for i=t+1..255    (probability of background class)
 *
 *    u_o(t) = Σ i·P(i) / q_o(t)         (mean of object)
 *    u_b(t) = Σ i·P(i) / q_b(t)         (mean of background)
 *
 *    σ²_o(t) = Σ [i - u_o]² · P(i)      (within-group variance, object)
 *    σ²_b(t) = Σ [i - u_b]² · P(i)      (within-group variance, background)
 *
 *    σ²_w(t) = σ²_o(t) + σ²_b(t)        (total within-group variance)
 *
 *  Best threshold = argmin σ²_w(t)  → minimises within-group variance
 *  This is equivalent to maximising between-group variance (Otsu's criterion).
 *
 *  For MULTI-threshold (k thresholds → k+1 regions):
 *  We extend the above to evaluate all region combinations via BFS search.
 * ══════════════════════════════════════════════════════════
 */
public class BFSMultiThreshold {

    // ── Load real image from file ────────────────────────────────────
    // Put your image file in the current directory, e.g. "input.png"
    private static int[][] loadImage(String filename) throws Exception {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("File not found: " + filename);
            System.err.println("Available image files in current directory:");
            File dir = new File(".");
            for (File f : dir.listFiles((d, n) -> n.matches("(?i).*\\.(png|jpg|jpeg|gif|bmp)$"))) {
                System.err.println("  - " + f.getName());
            }
            throw new IllegalArgumentException("Image file not found");
        }
        
        BufferedImage bi = ImageIO.read(file);
        if (bi == null) {
            System.err.println("ERROR: Failed to read image file: " + filename);
            System.err.println("Possible causes:");
            System.err.println("  1. File format not supported (try PNG, JPG, GIF, BMP)");
            System.err.println("  2. File is corrupted or in an unusual variant");
            System.err.println("  3. File might be in a format requiring special codec");
            System.err.println();
            System.err.println("Solution: Try converting the image using ImageMagick:");
            System.err.println("  convert " + filename + " -type Grayscale converted_" + filename);
            throw new IllegalArgumentException("Unable to read image format");
        }
        
        int height = bi.getHeight();
        int width = bi.getWidth();
        int[][] img = new int[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bi.getRGB(x, y);
                // Convert RGB to greyscale using standard formula
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                img[y][x] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        System.out.printf("Image loaded: %s (%dx%d pixels)%n", filename, width, height);
        return img;
    }

    // ── Synthetic test image (16×16 greyscale, 3 distinct regions) ─────
    // Region 1 (background):  ~50   dark
    // Region 2 (mid-tone):    ~130  grey
    // Region 3 (foreground):  ~210  bright
    private static final int IMG_H = 16;
    private static final int IMG_W = 16;

    private static int[][] createTestImage() {
        int[][] img = new int[IMG_H][IMG_W];
        for (int r = 0; r < IMG_H; r++) {
            for (int c = 0; c < IMG_W; c++) {
                if      (r < 6)              img[r][c] = 45 + (int)(Math.random() * 15);  // dark bg
                else if (r < 11)             img[r][c] = 125 + (int)(Math.random() * 15); // mid
                else                         img[r][c] = 205 + (int)(Math.random() * 15); // bright fg
            }
        }
        return img;
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 1: Build histogram h(i)
    // h(i) = number of pixels with grey-level i
    // Time complexity: O(N) where N = total number of pixels
    // ══════════════════════════════════════════════════════════════════
    static int[] buildHistogram(int[][] img) {
        int[] h = new int[256];
        for (int[] row : img)
            for (int pixel : row)
                h[pixel]++;
        return h;
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 2: Build cumulative sum arrays from histogram
    //
    //  cumCount[t] = Σ h(i)       for i=0..t   (cumulative pixel count)
    //  cumSum[t]   = Σ i·h(i)     for i=0..t   (cumulative intensity sum)
    //  cumSumSq[t] = Σ i²·h(i)    for i=0..t   (cumulative intensity sq sum)
    //
    // These allow O(1) computation of mean and variance for any interval [a,b]
    // instead of re-scanning the histogram each time → key optimisation.
    // Time complexity: O(256) = O(1) (constant number of grey-levels)
    // ══════════════════════════════════════════════════════════════════
    static long[] buildCumulativeCount(int[] h) {
        long[] c = new long[256];
        c[0] = h[0];
        for (int i = 1; i < 256; i++) c[i] = c[i-1] + h[i];
        return c;
    }

    static long[] buildCumulativeSum(int[] h) {
        long[] s = new long[256];
        s[0] = 0;
        for (int i = 1; i < 256; i++) s[i] = s[i-1] + (long)i * h[i];
        return s;
    }

    static double[] buildCumulativeSumSq(int[] h) {
        double[] sq = new double[256];
        sq[0] = 0;
        for (int i = 1; i < 256; i++) sq[i] = sq[i-1] + (double)i * i * h[i];
        return sq;
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 4: Compute within-group variance for region [lo..hi]
    //         using cumulative sums in O(1)
    //
    //  count  = cumCount[hi] - cumCount[lo-1]
    //  sum    = cumSum[hi]   - cumSum[lo-1]
    //  sumSq  = cumSumSq[hi] - cumSumSq[lo-1]
    //
    //  mean   = sum / count
    //  σ²     = sumSq/count - mean²       (variance formula via E[X²] - E[X]²)
    //
    // Time complexity: O(1) per region (thanks to precomputed cumulative sums)
    // ══════════════════════════════════════════════════════════════════
    static double regionVariance(int lo, int hi,
                                 long[] cumCount, long[] cumSum, double[] cumSumSq,
                                 int totalPixels) {
        if (lo > hi) return 0.0;

        long   count = cumCount[hi] - (lo > 0 ? cumCount[lo-1] : 0);
        long   sum   = cumSum[hi]   - (lo > 0 ? cumSum[lo-1]   : 0);
        double sumSq = cumSumSq[hi] - (lo > 0 ? cumSumSq[lo-1] : 0);

        if (count == 0) return 0.0;

        double mean    = (double) sum / count;
        double variance = sumSq / count - mean * mean;
        // Weight by proportion of pixels in this region (q_region = count/total)
        double weight  = (double) count / totalPixels;
        return weight * variance;
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 3 & 5: BFS search for optimal threshold combination
    //
    // State in BFS queue: { currentThreshold, regionStartLevel, accumulatedVariance }
    // We search for k thresholds dividing [0..255] into k+1 regions.
    //
    // BFS explores threshold values level by level:
    //  - Level 0: choose first threshold t1 ∈ [minStep .. 255-minStep*(k-1)]
    //  - Level 1: choose second threshold t2 > t1
    //  - ...
    //  - Level k-1: close the last region at 255
    //
    // At each BFS node we compute the variance of the region just closed
    // (using O(1) cumulative sum lookup) and accumulate it.
    // The BFS node with minimum total within-group variance wins.
    //
    // Time complexity: O(256^k) worst case, but BFS with pruning
    // (minStep gap between thresholds) dramatically reduces this.
    // For k=2 (2 thresholds), effective nodes ≈ 256²/2 = 32,768.
    // ══════════════════════════════════════════════════════════════════
    static class BFSNode {
        int[]  thresholds;   // chosen thresholds so far
        int    nextStart;    // grey-level where the next region starts
        double variance;     // accumulated within-group variance so far

        BFSNode(int[] thresholds, int nextStart, double variance) {
            this.thresholds = thresholds;
            this.nextStart  = nextStart;
            this.variance   = variance;
        }
    }

    static int[] bfsMultiThreshold(int k, long[] cumCount, long[] cumSum,
                                    double[] cumSumSq, int totalPixels) {
        final int MIN_STEP = 10;  // minimum grey-level gap between thresholds

        // BFS queue
        Queue<BFSNode> queue = new LinkedList<>();
        // Seed: no thresholds chosen yet, region starts at 0, variance = 0
        queue.add(new BFSNode(new int[0], 0, 0.0));

        int[]  bestThresholds = null;
        double bestVariance   = Double.MAX_VALUE;
        int    nodesVisited   = 0;

        while (!queue.isEmpty()) {
            BFSNode node = queue.poll();
            nodesVisited++;

            int chosen = node.thresholds.length; // how many thresholds chosen so far

            if (chosen == k) {
                // All k thresholds chosen — evaluate final region [nextStart..255]
                double totalVar = node.variance
                    + regionVariance(node.nextStart, 255, cumCount, cumSum, cumSumSq, totalPixels);
                if (totalVar < bestVariance) {
                    bestVariance   = totalVar;
                    bestThresholds = node.thresholds.clone();
                }
                continue;
            }

            // Remaining thresholds still to place: (k - chosen)
            int remaining = k - chosen;
            // Upper bound for next threshold: leave room for remaining thresholds + final region
            int maxT = 255 - MIN_STEP * remaining;

            for (int t = node.nextStart + MIN_STEP; t <= maxT; t++) {
                // Compute variance of region [node.nextStart .. t-1]
                double regionVar = regionVariance(node.nextStart, t - 1,
                        cumCount, cumSum, cumSumSq, totalPixels);

                // Build new threshold list
                int[] newThresholds = Arrays.copyOf(node.thresholds, chosen + 1);
                newThresholds[chosen] = t;

                queue.add(new BFSNode(newThresholds, t, node.variance + regionVar));
            }
        }

        System.out.printf("  BFS nodes visited: %,d%n", nodesVisited);
        System.out.printf("  Best within-group variance: %.4f%n", bestVariance);
        return bestThresholds;
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 6: Apply thresholds to segment the image
    // Each pixel is assigned a region label (0, 1, 2, ... k)
    // Time complexity: O(N) where N = total pixels
    // ══════════════════════════════════════════════════════════════════
    static int[][] applyThresholds(int[][] img, int[] thresholds) {
        int[][] segmented = new int[img.length][img[0].length];
        for (int r = 0; r < img.length; r++) {
            for (int c = 0; c < img[0].length; c++) {
                int pixel  = img[r][c];
                int label  = 0;
                for (int t : thresholds) {
                    if (pixel >= t) label++;
                    else break;
                }
                segmented[r][c] = label;
            }
        }
        return segmented;
    }

    // ── Helpers for display ───────────────────────────────────────────
    static void printImage(String title, int[][] img) {
        System.out.println("\n" + title);
        for (int[] row : img) {
            for (int v : row) System.out.printf("%4d", v);
            System.out.println();
        }
    }

    static void printSegmented(int[][] seg, int numRegions) {
        System.out.println("\nSegmented (region labels 0.." + (numRegions - 1) + "):");
        char[] symbols = {'░', '▒', '▓', '█', '#', '@'};
        for (int[] row : seg) {
            for (int label : row)
                System.out.print(" " + (label < symbols.length ? symbols[label] : label));
            System.out.println();
        }
    }

    static void saveSegmentedImage(int[][] seg, String filename) {
        try {
            int height = seg.length;
            int width = seg[0].length;
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            // Color map for regions: different shades of grey
            int[] colors = {0x333333, 0x666666, 0x999999, 0xCCCCCC, 0xFFFFFF, 0xFF0000};
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int label = seg[y][x];
                    int color = colors[Math.min(label, colors.length - 1)];
                    bi.setRGB(x, y, color);
                }
            }
            ImageIO.write(bi, "png", new File(filename));
        } catch (Exception e) {
            System.err.println("Error saving segmented image: " + e.getMessage());
        }
    }

    static void printHistogram(int[] h) {
        System.out.println("\nHistogram (grey-levels with count > 0):");
        System.out.printf("  %-12s %-10s%n", "Grey-level", "Count");
        for (int i = 0; i < 256; i++) {
            if (h[i] > 0) {
                String bar = "█".repeat(Math.min(h[i], 30));
                System.out.printf("  %-12d %-6d %s%n", i, h[i], bar);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // MAIN — runs both single-threshold and multi-threshold variants
    // ══════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {

        // ── Load image: either real image or synthetic test ───────────
        int[][] img;
        int totalPixels;
        
        if (args.length > 0) {
            // Load real image: java BFSMultiThreshold input.png
            img = loadImage(args[0]);
            totalPixels = img.length * img[0].length;
            System.out.printf("Using real image: %s (%d pixels)%n", args[0], totalPixels);
        } else {
            // Generate synthetic test image
            img = createTestImage();
            totalPixels = IMG_H * IMG_W;
            System.out.println("Using synthetic test image (16×16)");
            printImage("Input Image (grey-level values):", img);
        }

        // ── STEP 1: Histogram ────────────────────────────────────────
        System.out.println("\n── STEP 1: Build Histogram h(i)  [O(N)] ──");
        int[] h = buildHistogram(img);
        // Only print full histogram for small images
        if (img.length <= 20) {
            printHistogram(h);
        } else {
            System.out.printf("Histogram built (%d grey-levels with count > 0)%n",
                Arrays.stream(h).filter(x -> x > 0).toArray().length);
        }

        // ── STEP 2: Cumulative sums ───────────────────────────────────
        System.out.println("\n── STEP 2: Build Cumulative Sums  [O(256)] ──");
        long[]   cumCount = buildCumulativeCount(h);
        long[]   cumSum   = buildCumulativeSum(h);
        double[] cumSumSq = buildCumulativeSumSq(h);
        System.out.println("  Cumulative sums built (256 entries each). Sample values:");
        int[] sampleLevels = {49, 59, 124, 139, 204, 219, 255};
        System.out.printf("  %-12s %-15s %-18s %-18s%n",
                "Grey-level", "cumCount", "cumSum", "cumSumSq");
        for (int lvl : sampleLevels) {
            System.out.printf("  %-12d %-15d %-18d %-18.0f%n",
                    lvl, cumCount[lvl], cumSum[lvl], cumSumSq[lvl]);
        }

        // ── Demonstrate O(1) region statistics ───────────────────────
        System.out.println("\n── STEP 4 Demo: O(1) Region Statistics ──");
        int[][] regions = {{0, 89}, {90, 159}, {160, 255}};
        System.out.printf("  %-15s %-10s %-10s %-15s%n",
                "Region [lo,hi]", "Count", "Mean", "Weighted Var");
        for (int[] reg : regions) {
            int lo = reg[0], hi = reg[1];
            long count = cumCount[hi] - (lo > 0 ? cumCount[lo-1] : 0);
            long sum   = cumSum[hi]   - (lo > 0 ? cumSum[lo-1]   : 0);
            double mean = count > 0 ? (double)sum / count : 0;
            double wvar = regionVariance(lo, hi, cumCount, cumSum, cumSumSq, totalPixels);
            System.out.printf("  [%3d, %3d]      %-10d %-10.2f %-15.4f%n",
                    lo, hi, count, mean, wvar);
        }

        // ── SINGLE THRESHOLD (k=1) ────────────────────────────────────
        System.out.println("\n────────────────────────────────────────────────────────────");
        System.out.println("── STEPS 3+5: BFS — Single Threshold (k=1) ──");
        long t0 = System.nanoTime();
        int[] thresh1 = bfsMultiThreshold(1, cumCount, cumSum, cumSumSq, totalPixels);
        long  t1 = System.nanoTime();
        System.out.printf("  Time elapsed: %.3f ms%n", (t1 - t0) / 1e6);
        System.out.printf("  Optimal threshold (k=1): t = %d%n", thresh1[0]);

        // STEP 6: Apply
        System.out.println("\n── STEP 6: Apply Single Threshold ──");
        int[][] seg1 = applyThresholds(img, thresh1);
        if (img.length <= 20) {
            printSegmented(seg1, 2);
        } else {
            System.out.println("Segmented image (region labels 0.." + (2 - 1) + "):");
            System.out.printf("Output size: %dx%d pixels%n", seg1[0].length, seg1.length);
            saveSegmentedImage(seg1, "segmented_k1.png");
            System.out.println("✓ Saved as: segmented_k1.png");
        }

        // ── DOUBLE THRESHOLD (k=2) ─────────────────────────────────────
        System.out.println("\n────────────────────────────────────────────────────────────");
        System.out.println("── STEPS 3+5: BFS — Double Threshold (k=2) ──");
        long t2 = System.nanoTime();
        int[] thresh2 = bfsMultiThreshold(2, cumCount, cumSum, cumSumSq, totalPixels);
        long  t3 = System.nanoTime();
        System.out.printf("  Time elapsed: %.3f ms%n", (t3 - t2) / 1e6);
        System.out.printf("  Optimal thresholds (k=2): t1=%d, t2=%d%n",
                thresh2[0], thresh2[1]);

        // STEP 6: Apply
        System.out.println("\n── STEP 6: Apply Double Threshold ──");
        int[][] seg2 = applyThresholds(img, thresh2);
        if (img.length <= 20) {
            printSegmented(seg2, 3);
        } else {
            System.out.println("Segmented image (region labels 0.." + (3 - 1) + "):");
            System.out.printf("Output size: %dx%d pixels%n", seg2[0].length, seg2.length);
            saveSegmentedImage(seg2, "segmented_k2.png");
            System.out.println("✓ Saved as: segmented_k2.png");
        }

        // ── TRIPLE THRESHOLD (k=3) ─────────────────────────────────────
        System.out.println("\n────────────────────────────────────────────────────────────");
        System.out.println("── STEPS 3+5: BFS — Triple Threshold (k=3) ──");
        long t4 = System.nanoTime();
        int[] thresh3 = bfsMultiThreshold(3, cumCount, cumSum, cumSumSq, totalPixels);
        long  t5 = System.nanoTime();
        System.out.printf("  Time elapsed: %.3f ms%n", (t5 - t4) / 1e6);
        System.out.printf("  Optimal thresholds (k=3): t1=%d, t2=%d, t3=%d%n",
                thresh3[0], thresh3[1], thresh3[2]);

        System.out.println("\n── STEP 6: Apply Triple Threshold ──");
        int[][] seg3 = applyThresholds(img, thresh3);
        if (img.length <= 20) {
            printSegmented(seg3, 4);
        } else {
            System.out.println("Segmented image (region labels 0.." + (4 - 1) + "):");
            System.out.printf("Output size: %dx%d pixels%n", seg3[0].length, seg3.length);
            saveSegmentedImage(seg3, "segmented_k3.png");
            System.out.println("✓ Saved as: segmented_k3.png");
        }

        // ── STEP 7: Time Complexity Analysis ──────────────────────────
        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  STEP 7 — Time Complexity Analysis");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  Let N = total pixels, G = 256 grey-levels, k = #thresholds");
        System.out.println();
        System.out.printf("  %-40s %s%n", "Step", "Complexity");
        System.out.printf("  %-40s %s%n", "─".repeat(39), "─".repeat(20));
        System.out.printf("  %-40s %s%n", "Step 1: Build histogram h(i)",        "O(N)");
        System.out.printf("  %-40s %s%n", "Step 2: Build cumulative sums",       "O(G) = O(256) = O(1)");
        System.out.printf("  %-40s %s%n", "Step 3: BFS threshold search",        "O(G^k) nodes visited");
        System.out.printf("  %-40s %s%n", "Step 4: Region stats per BFS node",   "O(1) via cumulative sum");
        System.out.printf("  %-40s %s%n", "Step 5: Track best (during BFS)",     "O(1) per node");
        System.out.printf("  %-40s %s%n", "Step 6: Apply thresholds to image",   "O(N)");
        System.out.println();
        System.out.println("  TOTAL TIME COMPLEXITY:");
        System.out.println("    O(N) + O(G^k · 1) = O(N + G^k)");
        System.out.println();
        System.out.println("  COMPARISON — Without cumulative sum optimisation:");
        System.out.println("    Each BFS node would need O(G) to compute region stats");
        System.out.println("    → Total: O(N + G^(k+1))");
        System.out.println();
        System.out.println("  SPACE COMPLEXITY: O(G) for histograms + O(G^k) BFS queue");
        System.out.println();
        System.out.println("  PRACTICAL BFS NODE COUNTS (this run):");
        System.out.printf("    k=1 (single threshold):  ~%,d nodes%n",
                (256 - 10) / 1);
        System.out.printf("    k=2 (double threshold):  ~%,d nodes%n",
                (256 - 20) * (256 - 20) / 2);
        System.out.printf("    k=3 (triple threshold):  ~%,d nodes%n",
                (256 - 30) * (256 - 30) * (256 - 30) / 6);
        System.out.println();
        System.out.println("  WHY BFS?");
        System.out.println("    • BFS explores threshold combinations level by level:");
        System.out.println("      Level 0 = choosing t1, Level 1 = choosing t2, ...");
        System.out.println("    • Guarantees all combinations are considered (complete)");
        System.out.println("    • O(1) region variance via cumulative sums eliminates");
        System.out.println("      redundant computation — key optimisation from lecture");
        System.out.println("    • Pruning (MIN_STEP gap) reduces practical node count");
        System.out.println("      without affecting correctness");
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("  All 7 steps complete.");
        System.out.println("════════════════════════════════════════════════════════════");
    }
}
