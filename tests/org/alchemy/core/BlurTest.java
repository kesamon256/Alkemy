package org.alchemy.core;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Blur: only while Q is held, more with every pass without piling up shapes, mirrored, one undo step. */
public class BlurTest {

    static Rectangle2D bounds(int from) {
        Rectangle2D b = null;
        for (AlcShape s : Alchemy.canvas.shapes.subList(from, Alchemy.canvas.shapes.size())) {
            b = (b == null) ? s.getPath().getBounds2D() : b.createUnion(s.getPath().getBounds2D());
        }
        return b;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SmoothRepeatTest.held = AlcCanvas.class.getDeclaredField("motionHeld");
        SmoothRepeatTest.held.setAccessible(true);
        final int[] axis = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.setMotionHold(true);
                    c.resetView();
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(4);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    c.clear();
                    Field ax = AlcCanvas.class.getDeclaredField("axisX");
                    ax.setAccessible(true);
                    axis[0] = (int) Math.round(ax.getDouble(c));
                    c.setStyleMode(AlcCanvas.STYLE_MODE_FILL);
                    StyleTest.loop(axis[0] - 250, 300, 70);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(400);
        AlcCanvas c = Alchemy.canvas;
        UndoSmokeTest.check("a solid loop and its mirror image (" + c.shapes.size() + ")", c.shapes.size() == 2);
        double w0 = c.shapes.get(0).getPath().getBounds2D().getWidth();
        for (int i = 0; i < 3; i++) {
            SmoothRepeatTest.hover(axis[0] - 250, 300, false);
        }
        UndoSmokeTest.check("without Q nothing happens (" + c.shapes.size() + ")", c.shapes.size() == 2);
        SmoothRepeatTest.hover(axis[0] - 250, 300, true);
        int once = c.shapes.size();
        double w1 = bounds(0).getWidth();
        for (int i = 0; i < 4; i++) {
            SmoothRepeatTest.hover(axis[0] - 250 + i, 300, true);
        }
        int more = c.shapes.size();
        Rectangle2D all = bounds(0);
        SmoothRepeatTest.hover(axis[0] - 250, 300, false);
        UndoSmokeTest.check("Q blurs the loop and its mirror into soft copies (" + once + " shapes)", once == 50);
        UndoSmokeTest.check(String.format("more passes blur more (width %.0f -> %.0f -> %.0f) without more shapes (%d)", w0, w1, all.getWidth(), more),
                more == 50 && all.getWidth() > w1 + 4);
        UndoSmokeTest.check(String.format("both sides stay mirror images (centre %.1f, axis %d)", all.getCenterX(), axis[0]),
                Math.abs(all.getCenterX() - axis[0]) < 2);
        Thread.sleep(900);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.history.undo();
            }
        });
        UndoSmokeTest.check("one undo takes all the blurring back (" + c.shapes.size() + ")", c.shapes.size() == 2);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.history.redo();
                    SketchTest.shot("blur");
                    MarkerDisplaceTest.click("affectButton", "Blur");
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
