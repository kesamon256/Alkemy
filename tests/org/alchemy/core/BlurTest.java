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

    /** The widest glow line */
    static double widest() {
        double w = 0;
        for (AlcShape s : Alchemy.canvas.shapes) {
            w = Math.max(w, s.getLineWidth());
        }
        return w;
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
        double w1 = widest();
        for (int i = 0; i < 4; i++) {
            SmoothRepeatTest.hover(axis[0] - 250 + i, 300, true);
        }
        int more = c.shapes.size();
        Rectangle2D all = bounds(0);
        double w2 = widest();
        SmoothRepeatTest.hover(axis[0] - 250, 300, false);
        UndoSmokeTest.check("Q blurs the loop and its mirror into soft copies (" + once + " shapes)", once == 12);
        UndoSmokeTest.check(String.format("more passes blur more (glow %.0f -> %.0f px) without more shapes (%d)", w1, w2, more),
                more == 12 && w2 > w1 + 4);
        UndoSmokeTest.check(String.format("both sides stay mirror images (centre %.1f, axis %d)", all.getCenterX(), axis[0]),
                Math.abs(all.getCenterX() - axis[0]) < 2);
        Thread.sleep(900);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.history.undo();
            }
        });
        UndoSmokeTest.check("one undo takes all the blurring back (" + c.shapes.size() + ")", c.shapes.size() == 2);
        // A thin line (like the Flow brush) gets soft, it is not copied off to the sides
        final int lineY = 560;
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.canvas.setLineWidth(2);
                UndoSmokeTest.stroke(axis[0] - 400, lineY, axis[0] - 120, lineY + 20);
            }
        });
        int lineIndex = c.shapes.size() - 2;
        Rectangle2D lineBounds = c.shapes.get(lineIndex).getPath().getBounds2D();
        for (int i = 0; i < 3; i++) {
            SmoothRepeatTest.hover(axis[0] - 260, lineY + 10 + (int) (20 * Math.sin(5.0)), true);
        }
        SmoothRepeatTest.hover(axis[0] - 260, lineY + 10, false);
        boolean inPlace = true;
        int lineCopies = 0;
        for (AlcShape s : c.shapes) {
            Rectangle2D b = s.getPath().getBounds2D();
            if (b.getCenterX() < axis[0] && b.getMinY() > lineY - 40) {
                lineCopies++;
                inPlace &= Math.abs(b.getMinX() - lineBounds.getMinX()) < 0.5 && Math.abs(b.getMaxY() - lineBounds.getMaxY()) < 0.5;
            }
        }
        UndoSmokeTest.check("a thin line blurs in place - glow around it, no copies off to the side (" + lineCopies + " layers)", lineCopies == 6 && inPlace);
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
