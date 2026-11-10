package org.alchemy.core;

import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Clear on every layer, layer opacity as a group, transparent export, quick zoom / pan on heavy drawings,
 *  and screenshots of the new lasso / mirror look. */
public class ViewportTest {

    static boolean pending() throws Exception {
        Field f = AlcCanvas.class.getDeclaredField("viewPending");
        f.setAccessible(true);
        return f.getBoolean(Alchemy.canvas);
    }

    static int rgb(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1400, 860);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        final long[] zoomMs = new long[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.setColor(new Color(20, 20, 30));
                    c.setLineWidth(6);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);

                    // CLEAR - every layer, locked ones kept
                    UndoSmokeTest.stroke(100, 200, 400, 210);
                    c.addLayer();
                    UndoSmokeTest.stroke(100, 300, 400, 310);
                    c.addLayer();
                    UndoSmokeTest.stroke(100, 400, 400, 410);
                    c.setLayerLocked(c.getActiveLayer(), true);
                    c.clear();
                    java.util.List<AlcLayer> layers = c.getLayers();
                    UndoSmokeTest.check("clear empties the unlocked layers (" + layers.get(0).shapes.size() + ", " + layers.get(1).shapes.size() + ")",
                            layers.get(0).shapes.isEmpty() && layers.get(1).shapes.isEmpty());
                    UndoSmokeTest.check("clear keeps the locked layer", layers.get(2).shapes.size() == 1);
                    Alchemy.history.undoAction.actionPerformed(null);
                    UndoSmokeTest.check("one undo brings everything back", c.getLayers().get(0).shapes.size() == 1 && c.getLayers().get(1).shapes.size() == 1);
                    c.setLayerLocked(c.getActiveLayer(), false);
                    c.clear();
                    UndoSmokeTest.check("nothing left", !c.hasAnyShapes());

                    // LAYER OPACITY - crossing strokes on a 50% layer are as light as single ones
                    c.setActiveLayer(0);
                    c.setLineWidth(20);
                    UndoSmokeTest.stroke(200, 500, 600, 500);
                    UndoSmokeTest.stroke(400, 350, 400, 650);
                    c.setGuide(false);
                    BufferedImage full = (BufferedImage) c.renderCanvas(true, false, 1, c.getWidth(), c.getHeight());
                    c.setLayerOpacity(c.getActiveLayer(), 0.5F, true);
                    BufferedImage img = (BufferedImage) c.renderCanvas(true, false, 1, c.getWidth(), c.getHeight());
                    c.setGuide(true);
                    int bg = rgb(img, 5, 5) & 0xFF;
                    int worst = 0;
                    for (int x = 380; x <= 420; x++) {
                        int expected = (bg + (rgb(full, x, 500) & 0xFF)) / 2;
                        worst = Math.max(worst, Math.abs((rgb(img, x, 500) & 0xFF) - expected));
                    }
                    UndoSmokeTest.check("a 50% layer is the whole layer faded - crossings not darker (off by " + worst + ")", worst <= 2);

                    // TRANSPARENT EXPORT
                    BufferedImage t = (BufferedImage) c.renderCanvas(true, true, 1, c.getWidth(), c.getHeight());
                    int cornerAlpha = t.getRGB(5, 5) >>> 24, strokeAlpha = t.getRGB(300, 500) >>> 24;
                    UndoSmokeTest.check("transparent export: empty paper is clear (" + cornerAlpha + "), the stroke is not (" + strokeAlpha + ")",
                            cornerAlpha == 0 && strokeAlpha > 60);
                    c.setLayerOpacity(c.getActiveLayer(), 1F, true);
                    c.clear();

                    // HEAVY DRAWING - thousands of shapes
                    c.setLineWidth(3);
                    UndoSmokeTest.stroke(100, 100, 220, 160);
                    AlcShape seed = c.shapes.get(0);                    java.util.Random r = new java.util.Random(4);
                    for (int i = 0; i < 6000; i++) {
                        AlcShape copy = seed.customClone((GeneralPath) seed.getPath().clone());
                        copy.move(r.nextInt(1100), r.nextInt(650));
                        c.shapes.add(copy);
                    }
                    c.forceFullRedraw();
                    c.forceFullRedraw();
                    long start = System.nanoTime();
                    for (int i = 0; i < 10; i++) {
                        c.zoomAt(1.1, 600, 400);
                        c.paintImmediately(0, 0, c.getWidth(), c.getHeight());
                    }
                    zoomMs[0] = (System.nanoTime() - start) / 10000000L;
                    UndoSmokeTest.check("heavy drawing: zoom steps only move the picture (" + zoomMs[0] + " ms per step)", pending() && zoomMs[0] < 40);
                    SketchTest.shot("zoom-moving");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(700);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UndoSmokeTest.check("the view settles and is drawn sharply again", !pending());
                    SketchTest.shot("zoom-settled");
                    AlcCanvas c = Alchemy.canvas;
                    c.resetView();
                    c.clear();
                    // A few strokes, the mirror axis and a lasso selection
                    c.setLineWidth(5);
                    int fails = UndoSmokeTest.failures;
                    MirrorLayerDragTest.mirror();
                    UndoSmokeTest.failures = fails;
                    c.clear();
                    UndoSmokeTest.stroke(250, 250, 450, 300);
                    UndoSmokeTest.stroke(260, 380, 470, 330);
                    c.setSelecting(true);
                    LassoTest.loop(200, 200, 520, 450, 0);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("lasso-mirror-look");
                    Alchemy.canvas.setSelecting(false);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
