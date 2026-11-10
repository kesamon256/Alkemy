package org.alchemy.core;

import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/** Simulates a Windows Ink pen with varying pressure and checks the strokes. */
public class PressureTest implements AlcConstants {

    public static void main(String[] args) throws Exception {
        final String out = args[0];
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
                    SketchTest.dir = new java.io.File(out).getParent();
                    SketchTest.shot(new java.io.File(out).getName().replace(".png", ""));
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** A pen stroke whose pressure swells from light to heavy and back */
    static void penStroke(int x1, int x2, int y, boolean eraserEnd) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        AlcWindowsInk.update(true, eraserEnd, true, 0.05F, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, x1, y, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 100; i++) {
            float pressure = (float) Math.sin(Math.PI * i / 100.0);
            AlcWindowsInk.update(true, eraserEnd, true, 0.05F + 0.95F * pressure, 0, 0);
            int x = x1 + (x2 - x1) * i / 100;
            int yy = y + (int) (25 * Math.sin(i / 12.0));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, MouseEvent.BUTTON1_DOWN_MASK, x, yy, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 110, 0, x2, y, 1, false, MouseEvent.BUTTON1));
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        boolean oldEnabled = c.isPressureEnabled();
        UndoSmokeTest.check("Windows Ink hook installed on the window", AlcWindowsInk.isAvailable());
        c.setLineWidth(6);

        c.setPressureEnabled(true);
        penStroke(120, 1150, 260, false);
        AlcShape pressured = c.shapes.get(c.shapes.size() - 1);
        UndoSmokeTest.check("pen detected as stylus", c.getPenType() == PEN_STYLUS);
        UndoSmokeTest.check("pressure stroke is a filled variable-width shape", pressured.getStyle() == STYLE_FILL && pressured.hasSpine());
        java.util.ArrayList<Float> widths = pressured.getSpineWidth();
        float min = Float.MAX_VALUE, max = 0;
        for (Float w : widths) {
            min = Math.min(min, w);
            max = Math.max(max, w);
        }
        UndoSmokeTest.check(String.format("width follows pressure (%.1f .. %.1f px)", min * 2, max * 2), max > min * 4);
        UndoSmokeTest.check("tablet status shows Windows Ink", "Windows Ink".equals(c.getTabletStatus()));

        c.setPressureEnabled(false);
        penStroke(120, 1150, 460, false);
        AlcShape plain = c.shapes.get(c.shapes.size() - 1);
        UndoSmokeTest.check("pressure off -> even line", plain.getStyle() == STYLE_STROKE && !plain.hasSpine());

        // Pen leaves: the mouse takes over again
        AlcWindowsInk.update(false, false, false, 0, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 50, 50, 0, false, MouseEvent.NOBUTTON));
        UndoSmokeTest.check("mouse after pen -> cursor", c.getPenType() == PEN_CURSOR);
        UndoSmokeTest.check("mouse ignores pressure", true);
        c.setPressureEnabled(oldEnabled);
    }
}
