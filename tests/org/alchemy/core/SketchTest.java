package org.alchemy.core;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** Checks the sketching aids: eraser, stabilizer, paper, sketch files. */
public class SketchTest {

    static String dir;

    public static void main(String[] args) throws Exception {
        dir = new File(args[0]).getParent();
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1280, 760);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    test();
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** A jittery hand-drawn stroke */
    static void shakyStroke(int x1, int y1, int x2, int y2, int seed) {
        AlcCanvas c = Alchemy.canvas;
        java.util.Random r = new java.util.Random(seed);
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON1_DOWN_MASK, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 80; i++) {
            int x = x1 + (x2 - x1) * i / 80 + r.nextInt(13) - 6;
            int y = y1 + (y2 - y1) * i / 80 + r.nextInt(13) - 6;
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, MouseEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 90, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
    }

    static void rightDrag(int x1, int y1, int x2, int y2) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON3_DOWN_MASK, x1, y1, 1, false, MouseEvent.BUTTON3));
        for (int i = 1; i <= 10; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, MouseEvent.BUTTON3_DOWN_MASK,
                    x1 + (x2 - x1) * i / 10, y1 + (y2 - y1) * i / 10, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, 0, x2, y2, 1, false, MouseEvent.BUTTON3));
    }

    static void shot(String name) throws Exception {
        JRootPane root = Alchemy.window.getRootPane();
        BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        root.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        AlcHistory h = Alchemy.history;
        float oldStab = c.getStabilizer();
        int oldPaper = c.getPaper();
        c.setLineWidth(3);

        // Stabilizer comparison: top row raw, bottom row stabilized
        c.setStabilizer(0F);
        shakyStroke(120, 250, 1100, 250, 1);
        c.setStabilizer(0.75F);
        shakyStroke(120, 450, 1100, 450, 1);
        AlcShape raw = c.shapes.get(0), smooth = c.shapes.get(1);
        double rawJitter = jitter(raw, 250), smoothJitter = jitter(smooth, 450);
        UndoSmokeTest.check("stabilizer reduces jitter (" + String.format("%.1f -> %.1f px", rawJitter, smoothJitter) + ")", smoothJitter < rawJitter * 0.6);
        java.awt.Rectangle sb = smooth.getBounds();
        UndoSmokeTest.check("stabilized line reaches the end (right edge " + (sb.x + sb.width) + ")", sb.x + sb.width >= 1088);

        // Paper on screen, not in export
        c.setPaper(AlcCanvas.PAPER_GRID);
        shot("sketch-1-stabilizer-grid");
        File exported = new File(dir, "sketch-export.png");
        c.saveBitmap(exported);
        BufferedImage ex = ImageIO.read(exported);
        UndoSmokeTest.check("grid not exported", (ex.getRGB(32, 100) & 0xFFFFFF) == 0xFFFFFF);

        // Eraser: cut through the stabilized line only
        int before = c.shapes.size();
        rightDrag(600, 380, 600, 520);
        UndoSmokeTest.check("right drag erased one shape", c.shapes.size() == before - 1 && c.shapes.contains(raw) && !c.shapes.contains(smooth));
        h.undo();
        UndoSmokeTest.check("undo erase", c.shapes.size() == before);

        // Sketch file round trip
        File f = new File(dir, "test.alchemy");
        AlcSketchIO.save(f, c);
        c.clear();
        c.loadSketch(AlcSketchIO.open(f));
        UndoSmokeTest.check("sketch round trip keeps shapes", c.shapes.size() == before
                && c.shapes.get(0).getBounds().equals(raw.getBounds()) && c.shapes.get(0).lineWidth == raw.lineWidth);
        h.undo();
        UndoSmokeTest.check("undo open sketch", c.shapes.isEmpty());
        h.redo();

        c.setPaper(AlcCanvas.PAPER_DOTS);
        Alchemy.toolBar.setToolBarVisible(true);
        shot("sketch-2-dots");

        c.setStabilizer(oldStab);
        c.setPaper(oldPaper);
    }

    /** Average distance of the path points from the horizontal line y */
    static double jitter(AlcShape s, int y) {
        double sum = 0;
        int n = 0;
        float[] p = new float[6];
        for (java.awt.geom.PathIterator it = s.path.getPathIterator(null, 1); !it.isDone(); it.next()) {
            it.currentSegment(p);
            sum += Math.abs(p[1] - y);
            n++;
        }
        return sum / n;
    }

    static double length(AlcShape s) {
        double len = 0;
        float[] p = new float[6];
        float lx = 0, ly = 0;
        boolean first = true;
        for (java.awt.geom.PathIterator it = s.path.getPathIterator(null, 1); !it.isDone(); it.next()) {
            it.currentSegment(p);
            if (!first) {
                len += Math.hypot(p[0] - lx, p[1] - ly);
            }
            first = false;
            lx = p[0];
            ly = p[1];
        }
        return len;
    }
}
