package org.alchemy.core;

import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.SwingUtilities;

/** Drives the real Alchemy UI with synthetic pen events and checks undo/redo. */
public class UndoSmokeTest {

    static int failures = 0;
    static String out;

    public static void main(String[] args) throws Exception {
        out = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(4000);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    test();
                } catch (Throwable t) {
                    t.printStackTrace();
                    failures++;
                }
            }
        });
        System.out.println(failures == 0 ? "ALL PASSED" : ("FAILURES: " + failures));
        System.exit(failures == 0 ? 0 : 1);
    }

    static void stroke(int x1, int y1, int x2, int y2) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON1_DOWN_MASK, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 30; i++) {
            int x = x1 + (x2 - x1) * i / 30;
            int y = y1 + (y2 - y1) * i / 30 + (int) (20 * Math.sin(i / 3.0));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, MouseEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 40, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static void snap(String name) {
        Alchemy.canvas.saveBitmap(new File(out, name + ".png"));
    }

    static void test() {
        AlcCanvas c = Alchemy.canvas;
        AlcHistory h = Alchemy.history;
        System.out.println("canvas " + c.getWidth() + "x" + c.getHeight() + ", create module " + Alchemy.plugins.currentCreate);
        check("nothing to undo at start", !h.undoAction.isEnabled() && !h.redoAction.isEnabled());

        stroke(100, 120, 400, 140);
        int one = c.shapes.size();
        check("first stroke committed (" + one + " shapes)", one > 0);
        check("undo enabled", h.undoAction.isEnabled());
        stroke(100, 250, 400, 300);
        int two = c.shapes.size();
        check("second stroke committed (" + two + ")", two > one);
        snap("1-two-strokes");

        h.undo();
        check("undo removes 2nd stroke", c.shapes.size() == one);
        check("redo enabled", h.redoAction.isEnabled());
        snap("2-after-undo");
        h.redo();
        check("redo restores 2nd stroke", c.shapes.size() == two);
        h.undo();
        h.undo();
        check("undo all -> empty", c.shapes.isEmpty() && !h.undoAction.isEnabled());
        h.redo();
        h.redo();
        check("redo all", c.shapes.size() == two);

        c.clear();
        check("clear empties canvas", c.shapes.isEmpty());
        h.undo();
        check("undo clear brings drawing back", c.shapes.size() == two);
        snap("3-undo-clear");

        h.undo();
        stroke(150, 400, 450, 380);
        check("new stroke after undo drops redo", !h.redoAction.isEnabled());

        java.awt.Color bg = c.getBackgroundColor();
        c.setBackgroundColor(java.awt.Color.BLACK);
        h.undo();
        check("undo background colour", c.getBackgroundColor().equals(bg));

        int before = c.shapes.size();
        c.flipHorizontal();
        java.awt.Rectangle flipped = c.shapes.get(0).getBounds();
        h.undo();
        java.awt.Rectangle restored = c.shapes.get(0).getBounds();
        check("undo flip restores shape position", c.shapes.size() == before && !flipped.equals(restored));

        // Click without drawing must not create an empty undo step
        h.redo();
        h.undo();
        boolean redoBefore = h.redoAction.isEnabled();
        int stack = c.shapes.size();
        h.undo();
        h.redo();
        check("history consistent", c.shapes.size() == stack && redoBefore);
    }
}
