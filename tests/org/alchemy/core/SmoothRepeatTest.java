package org.alchemy.core;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Smooth and Repeat work only while Q is held, and follow the Mirror. */
public class SmoothRepeatTest {

    static Field held;

    static void hover(final int x, final int y, final boolean q) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    held.setBoolean(Alchemy.canvas, q);
                    AlcCanvas c = Alchemy.canvas;
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(260);
    }

    static String sig(AlcShape s) {
        java.awt.geom.PathIterator it = s.getPath().getPathIterator(null);
        double sum = 0;
        int n = 0;
        double[] p = new double[6];
        while (!it.isDone()) {
            it.currentSegment(p);
            sum += p[0] * 1.7 + p[1] * 3.1;
            n++;
            it.next();
        }
        return n + ":" + Math.round(sum);
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        held = AlcCanvas.class.getDeclaredField("motionHeld");
        held.setAccessible(true);
        final int[] axis = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.setMotionHold(true);
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(3);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    c.clear();
                    c.resetView();
                    Field ax = AlcCanvas.class.getDeclaredField("axisX");
                    ax.setAccessible(true);
                    axis[0] = (int) Math.round(ax.getDouble(c));
                    // A solid shape across the mirror line, as drawn with Displace - the two overlap
                    c.setStyleMode(AlcCanvas.STYLE_MODE_FILL);
                    StyleTest.loop(axis[0] - 40, 300, 70);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("affectButton", "Smooth");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(400);
        AlcCanvas c = Alchemy.canvas;
        UndoSmokeTest.check("loop and its mirror image (" + c.shapes.size() + ")", c.shapes.size() == 2);
        String a0 = sig(c.shapes.get(0)), b0 = sig(c.shapes.get(1));
        // Without Q, hovering changes nothing
        for (int i = 0; i < 4; i++) {
            hover(axis[0] - 60 + i * 3, 300, false);
        }
        UndoSmokeTest.check("Smooth without Q leaves the drawing alone", sig(c.shapes.get(0)).equals(a0) && sig(c.shapes.get(1)).equals(b0));
        // With Q, the loop and its mirror image are both smoothed
        for (int i = 0; i < 4; i++) {
            hover(axis[0] - 60 + i * 3, 300, true);
        }
        hover(axis[0] - 60, 300, false);
        java.awt.geom.Rectangle2D ra = c.shapes.get(0).getPath().getBounds2D(), rb = c.shapes.get(1).getPath().getBounds2D();
        UndoSmokeTest.check(String.format("both sides stay mirror images (%.1f %.1f / %.1f %.1f)", ra.getMinX() + ra.getMaxX(), rb.getMinX() + rb.getMaxX(), ra.getWidth(), rb.getWidth()),
                Math.abs((ra.getMinX() + rb.getMaxX()) / 2 - axis[0]) < 2 && Math.abs(ra.getWidth() - rb.getWidth()) < 1);
        String a1 = sig(c.shapes.get(0)), b1 = sig(c.shapes.get(1));
        UndoSmokeTest.check("Smooth with Q changes the loop (" + a0 + " -> " + a1 + ") and its mirror (" + b0 + " -> " + b1 + ")", !a1.equals(a0) && !b1.equals(b0));
        Thread.sleep(900);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.history.undo();
            }
        });
        UndoSmokeTest.check("one undo takes all the smoothing back", sig(c.shapes.get(0)).equals(a0) && sig(c.shapes.get(1)).equals(b0));

        // REPEAT
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    MarkerDisplaceTest.click("affectButton", "Smooth");
                    MarkerDisplaceTest.click("affectButton", "Repeat");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        int before = c.shapes.size() + c.affectShapes.size();
        for (int i = 0; i < 5; i++) {
            hover(axis[0] - 80 + i * 8, 300 + i * 5, false);
        }
        UndoSmokeTest.check("Repeat without Q adds nothing", c.shapes.size() + c.affectShapes.size() == before);
        for (int i = 0; i < 5; i++) {
            hover(axis[0] - 80 + i * 8, 300 + i * 5, true);
        }
        int left = 0, right = 0;
        for (AlcShape s : c.affectShapes) {
            if (s.getBounds().getCenterX() < axis[0]) {
                left++;
            } else {
                right++;
            }
        }
        UndoSmokeTest.check("Repeat with Q copies on both sides of the mirror (" + left + " / " + right + ")", left > 0 && left == right);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    held.setBoolean(Alchemy.canvas, false);
                    SketchTest.shot("smooth-repeat-mirror");
                    MarkerDisplaceTest.click("affectButton", "Repeat");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
