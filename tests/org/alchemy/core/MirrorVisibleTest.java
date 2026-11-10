package org.alchemy.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** With Mirror on, every brush stroke drawn with a pen must show on screen right away -
 *  the screen picture is compared with the drawing rendered from scratch. */
public class MirrorVisibleTest {

    static int dark(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                int rgb = img.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 600) {
                    n++;
                }
            }
        }
        return n;
    }

    static BufferedImage screen() {
        AlcCanvas c = Alchemy.canvas;
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        return img;
    }

    /** A pen stroke painted on screen after every move, as in the app - optionally with the
     *  canvas already working at a lower resolution, as it does when frames get slow */
    static void paintedStroke(int x1, int x2, int y, boolean lowRes) throws Exception {
        AlcCanvas c = Alchemy.canvas;
        java.lang.reflect.Field live = AlcCanvas.class.getDeclaredField("liveScale");
        live.setAccessible(true);
        long t = System.currentTimeMillis();
        AlcWindowsInk.update(true, false, true, 0.02F, 0, 0);
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, t, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        if (lowRes) {
            live.setDouble(c, 0.3);
        }
        for (int i = 1; i <= 60; i++) {
            float s = (float) Math.sin(Math.PI * i / 60.0);
            AlcWindowsInk.update(true, false, true, 0.02F + 0.98F * s * s, 0, 0);
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_DRAGGED, t + i * 10, java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                    x1 + (x2 - x1) * i / 60, y + (int) Math.round(6 * Math.sin(i / 7.0)), 0, false, java.awt.event.MouseEvent.NOBUTTON));
            screen();
        }
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_RELEASED, t + 700, 0, x2, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        AlcWindowsInk.update(false, false, false, 0, 0, 0);
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1500, 860);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                boolean oldSize = c.isPressureSize(), oldOpacity = c.isPressureOpacity();
                try {
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(6);
                    c.setPressureEnabled(true);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    System.out.println("mirror on: " + Alchemy.plugins.hasCurrentAffects());
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    String variant = System.getProperty("variant", "");
                    if (variant.contains("opacity")) {
                        c.setLayerOpacity(c.getActiveLayer(), 0.6F, true);
                    }
                    if (variant.contains("zoom")) {
                        c.zoomAt(1.5, 300, 300);
                    }
                    if (variant.contains("heavy")) {
                        // Lots of shapes so a full redraw is slow
                        c.setLineWidth(3);
                        UndoSmokeTest.stroke(100, 600, 220, 660);
                        AlcShape seed = c.shapes.get(c.shapes.size() - 1);
                        java.util.Random r = new java.util.Random(4);
                        for (int k = 0; k < 6000; k++) {
                            AlcShape copy = seed.customClone((java.awt.geom.GeneralPath) seed.getPath().clone());
                            copy.move(r.nextInt(1100), 550 + r.nextInt(200));
                            c.shapes.add(copy);
                        }
                        c.forceFullRedraw();
                        c.forceFullRedraw();
                        c.setLineWidth(6);
                    }
                    boolean[][] modes = {{true, false}, {false, true}, {true, true}};
                    String[] names = {"pencil", "ink", "marker", "charcoal", "liner", "brush pen", "sketchy", "hatching"};
                    int row = 0;
                    for (boolean[] m : modes) {
                        c.setPressureEffects(m[0], m[1]);
                        for (int i = 0; i < names.length; i++) {
                            select.invoke(brushes, i);
                            int y = 120 + row * 26;
                            row++;
                            java.lang.reflect.Field ax = AlcCanvas.class.getDeclaredField("axisX");
                            ax.setAccessible(true);
                            int axis = (int) Math.round(ax.getDouble(c));
                            if (System.getProperty("variant", "").contains("near")) {
                                // Start right by the mirror line - a stroke and a tap
                                paintedStroke(axis - 2, axis + 160, y, row % 2 == 0);
                                paintedStroke(axis + 3, axis + 3, y + 10, false);
                            } else {
                                paintedStroke(60, 500, y, row % 2 == 0);
                            }
                            int shown = dark(screen());
                            c.forceFullRedraw();
                            int full = dark(screen());
                            UndoSmokeTest.check(String.format("size=%b opacity=%b %s: on screen %d of %d dark pixels", m[0], m[1], names[i], shown, full),
                                    Math.abs(shown - full) <= Math.max(20, full / 50));
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                } finally {
                    c.setPressureEffects(oldSize, oldOpacity);
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("mirror-visible");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
