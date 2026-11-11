package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/**
 * Gradient along the stroke: a U shaped line starts in the first colour and ends in the second,
 * around the bend - a straight gradient across could not do that. Also with Ink (a spine shape),
 * Flow (many shapes), fading out, in a saved sketch, and line weights below 1.
 */
public class GradientAlongTest {

    /** A U: right along the top, down, back left along the bottom */
    static void uStroke() {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        int n = 0;
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, 150, 250, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 150, 250, 1, false, MouseEvent.BUTTON1));
        int[][] pts = new int[90][];
        for (int i = 0; i < 40; i++) {
            pts[n++] = new int[]{150 + i * 12, 250};
        }
        for (int i = 0; i <= 10; i++) {
            double a = -Math.PI / 2 + Math.PI * i / 10;
            pts[n++] = new int[]{630 + (int) (Math.cos(a) * 80), 330 + (int) (Math.sin(a) * 80)};
        }
        for (int i = 1; i < 39; i++) {
            pts[n++] = new int[]{630 - i * 12, 410};
        }
        for (int i = 1; i < n; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 8, InputEvent.BUTTON1_DOWN_MASK, pts[i][0], pts[i][1], 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + n * 8 + 10, 0, pts[n - 1][0], pts[n - 1][1], 1, false, MouseEvent.BUTTON1));
    }

    static BufferedImage render() {
        AlcCanvas c = Alchemy.canvas;
        return (BufferedImage) c.renderCanvas(true, false, 1, c.getWidth(), c.getHeight());
    }

    /** The reddest / bluest of a few pixels around a point - a line is only some pixels wide */
    static Color around(BufferedImage img, int x, int y) {
        Color best = new Color(img.getRGB(x, y));
        int bestInk = 0;
        for (int dy = -3; dy <= 3; dy++) {
            Color p = new Color(img.getRGB(x, y + dy));
            int ink = 765 - p.getRed() - p.getGreen() - p.getBlue() + Math.abs(p.getRed() - p.getBlue());
            if (ink > bestInk) {
                bestInk = ink;
                best = p;
            }
        }
        return best;
    }

    /** The reddest or bluest pixel in a box around a point - Flow is random, its trails wander */
    static Color most(BufferedImage img, int x, int y, boolean red) {
        Color best = Color.WHITE;
        int bestScore = -999;
        for (int dy = -25; dy <= 25; dy++) {
            for (int dx = -25; dx <= 25; dx++) {
                Color p = new Color(img.getRGB(x + dx, y + dy));
                int score = red ? p.getRed() - p.getBlue() : p.getBlue() - p.getRed();
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    static String rgb(Color c) {
        return c.getRed() + "," + c.getGreen() + "," + c.getBlue();
    }

    static Field endField;

    static void setup(String create, int brush, int style, float width) throws Exception {
        AlcCanvas c = Alchemy.canvas;
        c.clear();
        MarkerDisplaceTest.click("createButton", create);
        if (brush >= 0) {
            AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
            Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
            select.setAccessible(true);
            select.invoke(brushes, brush);
        }
        c.setStyleMode(style);
        c.setLineWidth(width);
        c.setColor(new Color(220, 30, 30));
    }

    static AlcModule gradient() {
        for (AlcModule m : Alchemy.plugins.affects) {
            if (m.getClass().getSimpleName().equals("Gradient")) {
                return m;
            }
        }
        return null;
    }

    static AlcAbstractSpinner findSpinner(Container root, String title) {
        for (Component child : root.getComponents()) {
            if (child instanceof AlcAbstractSpinner && title.equals(((AlcAbstractSpinner) child).label.getText())) {
                return (AlcAbstractSpinner) child;
            }
            if (child instanceof Container) {
                AlcAbstractSpinner found = findSpinner((Container) child, title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final float savedWidth = Alchemy.canvas.getLineWidth();
        final Object[] savedEnd = new Object[2];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    Alchemy.canvas.resetView();
                    MarkerDisplaceTest.click("affectButton", "Gradient");
                    AlcModule g = gradient();
                    endField = g.getClass().getDeclaredField("end");
                    endField.setAccessible(true);
                    savedEnd[0] = endField.get(g);
                    Field fade = g.getClass().getDeclaredField("fadeOut");
                    fade.setAccessible(true);
                    savedEnd[1] = fade.get(g);
                    fade.setBoolean(g, false);
                    endField.set(g, new Color(30, 60, 230));
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        // A U with the Shapes line, then Ink, then Flow
        Object[][] cases = {{"Shapes", -1, AlcCanvas.STYLE_MODE_STROKE, 12F}, {"Brushes", 1, AlcCanvas.STYLE_MODE_STROKE, 14F}, {"Brushes", 8, AlcCanvas.STYLE_MODE_STROKE, 12F}};
        for (final Object[] k : cases) {
            final BufferedImage[] img = new BufferedImage[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        setup((String) k[0], (Integer) k[1], (Integer) k[2], (Float) k[3]);
                        uStroke();
                        img[0] = render();
                        SketchTest.shot("gradient-" + k[0] + k[1]);
                        if (Boolean.getBoolean("plain")) {
                            // The same without the gradient, to compare the edges
                            MarkerDisplaceTest.click("affectButton", "Gradient");
                            setup((String) k[0], (Integer) k[1], (Integer) k[2], (Float) k[3]);
                            uStroke();
                            javax.imageio.ImageIO.write(render(), "png", new java.io.File(SketchTest.dir, "plain-" + k[0] + k[1] + ".png"));
                            javax.imageio.ImageIO.write(img[0], "png", new java.io.File(SketchTest.dir, "along-" + k[0] + k[1] + ".png"));
                            MarkerDisplaceTest.click("affectButton", "Gradient");
                            setup((String) k[0], (Integer) k[1], (Integer) k[2], (Float) k[3]);
                            uStroke();
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        UndoSmokeTest.failures++;
                    }
                }
            });
            Thread.sleep(600);
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    img[0] = render();
                }
            });
            String name = k[0] + (((Integer) k[1]) >= 0 ? " " + k[1] : "");
            boolean flow = ((Integer) k[1]) == 8;
            Color start = flow ? most(img[0], 175, 250, true) : around(img[0], 175, 250);
            Color end = flow ? most(img[0], 190, 410, false) : around(img[0], 190, 410);
            Color bend = around(img[0], 710, 330);
            UndoSmokeTest.check(name + ": starts red (" + rgb(start) + ")", start.getRed() > start.getBlue() + 60);
            UndoSmokeTest.check(name + ": ends blue, back on the same side (" + rgb(end) + ")", end.getBlue() > end.getRed() + 60);
            if (!flow) {
                UndoSmokeTest.check(name + ": half way round the bend it is in between (" + rgb(bend) + ")",
                        bend.getRed() > 60 && bend.getBlue() > 60 && Math.abs(bend.getRed() - bend.getBlue()) < 120);
            }
        }
        // Fading out instead
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcModule g = gradient();
                    Field fade = g.getClass().getDeclaredField("fadeOut");
                    fade.setAccessible(true);
                    fade.setBoolean(g, true);
                    setup("Shapes", -1, AlcCanvas.STYLE_MODE_STROKE, 12F);
                    uStroke();
                    BufferedImage img = render();
                    Color start = around(img, 175, 250), end = new Color(img.getRGB(175, 410));
                    UndoSmokeTest.check("fade out: starts red (" + rgb(start) + "), ends almost gone (" + rgb(end) + ")",
                            start.getRed() > 150 && end.getRed() > 220 && end.getGreen() > 220);
                    fade.setBoolean(g, false);
                    // Saved in a sketch and opened again
                    setup("Shapes", -1, AlcCanvas.STYLE_MODE_STROKE, 12F);
                    uStroke();
                    java.io.File file = new java.io.File(SketchTest.dir, "gradient.alchemy");
                    AlcSketchIO.save(file, Alchemy.canvas);
                    AlcSketchIO.Sketch sketch = AlcSketchIO.open(file);
                    AlcShape back = sketch.layers.get(sketch.activeLayer).shapes.get(0);
                    UndoSmokeTest.check("a saved sketch keeps the gradient (" + back.getAlongColor() + ")",
                            back.getAlongColor() != null && back.getAlongColor().getBlue() == 230 && back.getAlongTo() > 0.9F);
                    file.delete();
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        // Line weights below 1
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    MarkerDisplaceTest.click("affectButton", "Gradient");
                    AlcAbstractSpinner spinner = findSpinner(Alchemy.toolBar, Alchemy.bundle.getString("lineWeightTitle"));
                    UndoSmokeTest.check("the line weight box is there", spinner != null);
                    spinner.setNumber(0.3F);
                    UndoSmokeTest.check("the line weight box goes to 0.3 (" + Alchemy.canvas.getLineWidth() + ")", Math.abs(Alchemy.canvas.getLineWidth() - 0.3F) < 0.001);
                    spinner.setPreviousValue();
                    spinner.setPreviousValue();
                    spinner.setPreviousValue();
                    float lowest = spinner.getNumber();
                    spinner.setNumber(1F);
                    spinner.setNextValue();
                    UndoSmokeTest.check("it steps down to 0.1 and from 1 up to 2 (" + lowest + ", " + spinner.getNumber() + ")",
                            Math.abs(lowest - 0.1F) < 0.001 && spinner.getNumber() == 2F);
                    // A fine line is drawn thinner and lighter than a 1 px line
                    AlcCanvas c = Alchemy.canvas;
                    c.clear();
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    c.setColor(Color.BLACK);
                    c.setLineWidth(0.3F);
                    UndoSmokeTest.stroke(200, 300, 700, 300);
                    float drawn = c.shapes.get(c.shapes.size() - 1).getLineWidth();
                    BufferedImage thin = render();
                    c.clear();
                    c.setLineWidth(1F);
                    UndoSmokeTest.stroke(200, 300, 700, 300);
                    BufferedImage one = render();
                    long inkThin = ink(thin), inkOne = ink(one);
                    UndoSmokeTest.check(String.format("a 0.3 line is drawn 0.3 wide (%.2f) and fainter than a 1 px line (%d < %d)", drawn, inkThin, inkOne),
                            Math.abs(drawn - 0.3F) < 0.001 && inkThin > 0 && inkThin < inkOne * 0.6);
                    // Brushes get finer too
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    select.invoke(brushes, 1);
                    c.clear();
                    c.setLineWidth(0.3F);
                    UndoSmokeTest.stroke(200, 300, 700, 300);
                    long inkInkThin = ink(render());
                    c.clear();
                    c.setLineWidth(1F);
                    UndoSmokeTest.stroke(200, 300, 700, 300);
                    long inkInkOne = ink(render());
                    UndoSmokeTest.check("Ink at 0.3 is finer than at 1 (" + inkInkThin + " < " + inkInkOne + ")", inkInkThin > 0 && inkInkThin < inkInkOne * 0.6);
                    c.clear();
                    spinner.setNumber(savedWidth);
                    c.setLineWidth(savedWidth);
                    AlcModule g = gradient();
                    endField.set(g, savedEnd[0]);
                    Field fade = g.getClass().getDeclaredField("fadeOut");
                    fade.setAccessible(true);
                    fade.set(g, savedEnd[1]);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** How much darker than white the picture is, all together */
    static long ink(BufferedImage img) {
        long sum = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                sum += 255 - (img.getRGB(x, y) & 0xFF);
            }
        }
        return sum;
    }
}
